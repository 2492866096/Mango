/*
 * Copyright © 2009 Nokia Corporation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice (including the next
 * paragraph) shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * Author:  Siarhei Siamashka (siarhei.siamashka@nokia.com)
 */

/*
 * This file contains implementations of NEON optimized pixel processing
 * functions. There is no full and detailed tutorial, but some functions
 * (those which are exposing some new or interesting features) are
 * extensively commented and can be used as examples.
 *
 * You may want to have a look at the comments for following functions:
 *  - pixman_composite_over_8888_0565_asm_neon
 *  - pixman_composite_over_n_8_0565_asm_neon
 */

/* Prevent the stack from becoming executable for no reason... */
#if defined(__linux__) && defined(__ELF__)
.section .note.GNU-stack,"",%progbits
#endif

.text
.arch armv8-a

.altmacro
.p2align 2

#include "pixman-private.h"
#include "pixman-arm-asm.h"
#include "pixman-arma64-neon-asm.h"

/* Global configuration options and preferences */

/*
 * The code can optionally make use of unaligned memory accesses to improve
 * performance of handling leading/trailing pixels for each scanline.
 * Configuration variable RESPECT_STRICT_ALIGNMENT can be set to 0 for
 * example in linux if unaligned memory accesses are not configured to
 * generate.exceptions.
 */
.set RESPECT_STRICT_ALIGNMENT, 1

/*
 * Set default prefetch type. There is a choice between the following options:
 *
 * PREFETCH_TYPE_NONE (may be useful for the ARM cores where PLD is set to work
 * as NOP to workaround some HW bugs or for whatever other reason)
 *
 * PREFETCH_TYPE_SIMPLE (may be useful for simple single-issue ARM cores where
 * advanced prefetch intruduces heavy overhead)
 *
 * PREFETCH_TYPE_ADVANCED (useful for superscalar cores such as ARM Cortex-A8
 * which can run ARM and NEON instructions simultaneously so that extra ARM
 * instructions do not add (many) extra cycles, but improve prefetch efficiency)
 *
 * Note: some types of function can't support advanced prefetch and fallback
 *       to simple one (those which handle 24bpp pixels)
 */
.set PREFETCH_TYPE_DEFAULT, PREFETCH_TYPE_ADVANCED

/* Prefetch distance in pixels for simple prefetch */
.set PREFETCH_DISTANCE_SIMPLE, 64

/*
 * Implementation of pixman_composite_over_8888_0565_asm_neon
 *
 * This function takes a8r8g8b8 source buffer, r5g6b5 destination buffer and
 * performs OVER compositing operation. Function fast_composite_over_8888_0565
 * from pixman-fast-path.c does the same in C and can be used as a reference.
 *
 * First we need to have some NEON assembly code which can do the actual
 * operation on the pixels and provide it to the template macro.
 *
 * Template macro quite conveniently takes care of emitting all the necessary
 * code for memory reading and writing (including quite tricky cases of
 * handling unaligned leading/trailing pixels), so we only need to deal with
 * the data in NEON registers.
 *
 * NEON registers allocation in general is recommented to be the following:
 * v0,  v1,  v2,  v3  - contain loaded source pixel data
 * v4,  v5,  v6,  v7  - contain loaded destination pixels (if they are needed)
 * v24, v25, v26, v27 - contain loading mask pixel data (if mask is used)
 * v28, v29, v30, v31 - place for storing the result (destination pixels)
 *
 * As can be seen above, four 64-bit NEON registers are used for keeping
 * intermediate pixel data and up to 8 pixels can be processed in one step
 * for 32bpp formats (16 pixels for 16bpp, 32 pixels for 8bpp).
 *
 * This particular function uses the following registers allocation:
 * v0,  v1,  v2,  v3  - contain loaded source pixel data
 * v4,  v5            - contain loaded destination pixels (they are needed)
 * v28, v29           - place for storing the result (destination pixels)
 */

/*
 * Step one. We need to have some code to do some arithmetics on pixel data.
 * This is implemented as a pair of macros: '*_head' and '*_tail'. When used
 * back-to-back, they take pixel data from {v0, v1, v2, v3} and {v4, v5},
 * perform all the needed calculations and write the result to {v28, v29}.
 * The rationale for having two macros and not just one will be explained
 * later. In practice, any single monolitic function which does the work can
 * be split into two parts in any arbitrary way without affecting correctness.
 *
 * There is one special trick here too. Common template macro can optionally
 * make our life a bit easier by doing R, G, B, A color components
 * deinterleaving for 32bpp pixel formats (and this feature is used in
 * 'pixman_composite_over_8888_0565_asm_neon' function). So it means that
 * instead of having 8 packed pixels in {v0, v1, v2, v3} registers, we
 * actually use v0 register for blue channel (a vector of eight 8-bit
 * values), v1 register for green, v2 for red and v3 for alpha. This
 * simple conversion can be also done with a few NEON instructions:
 *
 * Packed to planar conversion: // vuzp8 is a wrapper macro
 *  vuzp8 v0, v1
 *  vuzp8 v2, v3
 *  vuzp8 v1, v3
 *  vuzp8 v0, v2
 *
 * Planar to packed conversion: // vzip8 is a wrapper macro
 *  vzip8 v0, v2
 *  vzip8 v1, v3
 *  vzip8 v2, v3
 *  vzip8 v0, v1
 *
 * But pixel can be loaded directly in planar format using LD4 / b NEON
 * instruction. It is 1 cycle slower than LD1 / s, so this is not always
 * desirable, that's why deinterleaving is optional.
 *
 * But anyway, here is the code:
 */

.macro pixman_composite_over_8888_0565_process_pixblock_head
    /* convert 8 r5g6b5 pixel data from {v4} to planar 8-bit format
       and put data into v6 - red, v7 - green, v30 - blue */
    mov         v4.d[1], v5.d[0]
    shrn        v6.8b, v4.8h, #8
    shrn        v7.8b, v4.8h, #3
    sli         v4.8h, v4.8h, #5
    sri         v6.8b, v6.8b, #5
    mvn         v3.8b, v3.8b      /* invert source alpha */
    sri         v7.8b, v7.8b, #6
    shrn        v30.8b, v4.8h, #2
    /* now do alpha blending, storing results in 8-bit planar format
       into v20 - red, v23 - green, v22 - blue */
    umull       v10.8h, v3.8b, v6.8b
    umull       v11.8h, v3.8b, v7.8b
    umull       v12.8h, v3.8b, v30.8b
    urshr       v17.8h, v10.8h, #8
    urshr       v18.8h, v11.8h, #8
    urshr       v19.8h, v12.8h, #8
    raddhn      v20.8b, v10.8h, v17.8h
    raddhn      v23.8b, v11.8h, v18.8h
    raddhn      v22.8b, v12.8h, v19.8h
.endm

.macro pixman_composite_over_8888_0565_process_pixblock_tail
    /* ... continue alpha blending */
    uqadd       v17.8b, v2.8b, v20.8b
    uqadd       v18.8b, v0.8b, v22.8b
    uqadd       v19.8b, v1.8b, v23.8b
    /* convert the result to r5g6b5 and store it into {v14} */
    ushll       v14.8h, v17.8b, #7
    sli         v14.8h, v14.8h, #1
    ushll       v8.8h, v19.8b, #7
    sli         v8.8h, v8.8h, #1
    ushll       v9.8h, v18.8b, #7
    sli         v9.8h, v9.8h, #1
    sri         v14.8h, v8.8h, #5
    sri         v14.8h, v9.8h, #11
    mov         v28.d[0], v14.d[0]
    mov         v29.d[0], v14.d[1]
.endm

/*
 * OK, now we got almost everything that we need. Using the above two
 * macros, the work can be done right. But now we want to optimize
 * it a bit. ARM Cortex-A8 is an in-order core, and benefits really
 * a lot from good code scheduling and software pipelining.
 *
 * Let's construct some code, which will run in the core main loop.
 * Some pseudo-code of the main loop will look like this:
 *   head
 *   while (...) {
 *     tail
 *     head
 *   }
 *   tail
 *
 * It may look a bit weird, but this setup allows to hide instruction
 * latencies better and also utilize dual-issue capability more
 * efficiently (make pairs of load-store and ALU instructions).
 *
 * So what we need now is a '*_tail_head' macro, which will be used
 * in the core main loop. A trivial straightforward implementation
 * of this macro would look like this:
 *
 *   pixman_composite_over_8888_0565_process_pixblock_tail
 *   st1         {v28.4h, v29.4h}, [DST_W], #32
 *   ld1         {v4.4h, v5.4h}, [DST_R], #16
 *   ld4         {v0.2s, v1.2s, v2.2s, v3.2s}, [SRC], #32
 *   pixman_composite_over_8888_0565_process_pixblock_head
 *   cache_preload 8, 8
 *
 * Now it also got some VLD/VST instructions. We simply can't move from
 * processing one block of pixels to the other one with just arithmetics.
 * The previously processed data needs to be written to memory and new
 * data needs to be fetched. Fortunately, this main loop does not deal
 * with partial leading/trailing pixels and can load/store a full block
 * of pixels in a bulk. Additionally, destination buffer is already
 * 16 bytes aligned here (which is good for performance).
 *
 * New things here are DST_R, DST_W, SRC and MASK identifiers. These
 * are the aliases for ARM registers which are used as pointers for
 * accessing data. We maintain separate pointers for reading and writing
 * destination buffer (DST_R and DST_W).
 *
 * Another new thing is 'cache_preload' macro. It is used for prefetching
 * data into CPU L2 cache and improve performance when dealing with large
 * images which are far larger than cache size. It uses one argument
 * (actually two, but they need to be the same here) - number of pixels
 * in a block. Looking into 'pixman-arm-neon-asm.h' can provide some
 * details about this macro. Moreover, if good performance is needed
 * the code from this macro needs to be copied into '*_tail_head' macro
 * and mixed with the rest of code for optimal instructions scheduling.
 * We are actually doing it below.
 *
 * Now after all the explanations, here is the optimized code.
 * Different instruction streams (originaling from '*_head', '*_tail'
 * and 'cache_preload' macro) use different indentation levels for
 * better readability. Actually taking the code from one of these
 * indentation levels and ignoring a few LD/ST instructions would
 * result in exactly the code from '*_head', '*_tail' or 'cache_preload'
 * macro!
 */

#if 1

.macro pixman_composite_over_8888_0565_process_pixblock_tail_head
        uqadd       v17.8b, v2.8b, v20.8b
    ld1         {v4.4h, v5.4h}, [DST_R], #16
    mov         v4.d[1], v5.d[0]
        uqadd       v18.8b, v0.8b, v22.8b
        uqadd       v19.8b, v1.8b, v23.8b
    shrn        v6.8b, v4.8h, #8
    fetch_src_pixblock
    shrn        v7.8b, v4.8h, #3
    sli         v4.8h, v4.8h, #5
        ushll       v14.8h, v17.8b, #7
        sli         v14.8h, v14.8h, #1
                                    PF add, PF_X, PF_X, #8
        ushll       v8.8h, v19.8b, #7
        sli         v8.8h, v8.8h,  #1
                                    PF tst, PF_CTL, #0xF
    sri         v6.8b, v6.8b, #5
                                    PF beq, 10f
                                    PF add, PF_X, PF_X, #8
10:
    mvn         v3.8b, v3.8b
                                    PF beq, 10f
                                    PF sub, PF_CTL, PF_CTL, #1
10:
    sri         v7.8b, v7.8b, #6
    shrn        v30.8b, v4.8h, #2
    umull       v10.8h, v3.8b, v6.8b
                                    PF lsl, DUMMY, PF_X, #src_bpp_shift
                                    PF prfm, PREFETCH_MODE, [PF_SRC, DUMMY]
    umull       v11.8h, v3.8b, v7.8b
    umull       v12.8h, v3.8b, v30.8b
                                    PF lsl, DUMMY, PF_X, #dst_bpp_shift
                                    PF prfm, PREFETCH_MODE, [PF_DST, DUMMY]
        sri         v14.8h, v8.8h, #5
                                    PF cmp, PF_X, ORIG_W
        ushll       v9.8h, v18.8b, #7
        sli         v9.8h, v9.8h, #1
    urshr       v17.8h, v10.8h, #8
                                    PF ble, 10f
                                    PF sub, PF_X, PF_X, ORIG_W
10:
    urshr       v19.8h, v11.8h, #8
    urshr       v18.8h, v12.8h, #8
                                    PF ble, 10f
                                    PF subs, PF_CTL, PF_CTL, #0x10
10:
        sri         v14.8h, v9.8h, #11
        mov         v28.d[0], v14.d[0]
        mov         v29.d[0], v14.d[1]
                                    PF ble, 10f
                                    PF add, PF_SRC, PF_SRC, SRC_STRIDE, lsl #src_bpp_shift
                                    PF ldrsb, DUMMY, [PF_SRC]
10:
    raddhn      v20.8b, v10.8h, v17.8h
    raddhn      v23.8b, v11.8h, v19.8h
                                    PF ble, 10f
                                    PF add, PF_DST, PF_DST, DST_STRIDE, lsl #dst_bpp_shift
                                    PF ldrsb, DUMMY, [PF_DST]
10:
    raddhn      v22.8b, v12.8h, v18.8h
        st1         {v14.8h}, [DST_W], #16
.endm

#else

/* If we did not care much about the performance, we would just use this... */
.macro pixman_composite_over_8888_0565_process_pixblock_tail_head
    pixman_composite_over_8888_0565_process_pixblock_tail
    st1         {v14.8h}, [DST_W], #16
    ld1         {v4.4h, v4.5h}, [DST_R], #16
    fetch_src_pixblock
    pixman_composite_over_8888_0565_process_pixblock_head
    cache_preload 8, 8
.endm

#endif

/*
 * And now the final part. We are using 'generate_composite_function' macro
 * to put all the stuff together. We are specifying the name of the function
 * which we want to get, number of bits per pixel for the source, mask and
 * destination (0 if unused, like mask in this case). Next come some bit
 * flags:
 *   FLAG_DST_READWRITE      - tells that the destination buffer is both read
 *                             and written, for write-only buffer we would use
 *                             FLAG_DST_WRITEONLY flag instead
 *   FLAG_DEINTERLEAVE_32BPP - tells that we prefer to work with planar data
 *                             and separate color channels for 32bpp format.
 * The next things are:
 *  - the number of pixels processed per iteration (8 in this case, because
 *    that's the maximum what can fit into four 64-bit NEON registers).
 *  - prefetch distance, measured in pixel blocks. In this case it is 5 times
 *    by 8 pixels. That would be 40 pixels, or up to 160 bytes. Optimal
 *    prefetch distance can be selected by running some benchmarks.
 *
 * After that we specify some macros, these are 'default_init',
 * 'default_cleanup' here which are empty (but it is possible to have custom
 * init/cleanup macros to be able to save/restore some extra NEON registers
 * like d8-d15 or do anything else) followed by
 * 'pixman_composite_over_8888_0565_process_pixblock_head',
 * 'pixman_composite_over_8888_0565_process_pixblock_tail' and
 * 'pixman_composite_over_8888_0565_process_pixblock_tail_head'
 * which we got implemented above.
 *
 * The last part is the NEON registers allocation scheme.
 */

/* Macros from other functions that the ones below build on. */
.macro pixman_composite_out_reverse_8888_8888_process_pixblock_head
    mvn         v24.8b, v3.8b  /* get inverted alpha */
    /* do alpha blending */
    umull       v8.8h, v24.8b, v4.8b
    umull       v9.8h, v24.8b, v5.8b
    umull       v10.8h, v24.8b, v6.8b
    umull       v11.8h, v24.8b, v7.8b
.endm


.macro pixman_composite_out_reverse_8888_8888_process_pixblock_tail
    urshr       v14.8h, v8.8h, #8
    urshr       v15.8h, v9.8h, #8
    urshr       v16.8h, v10.8h, #8
    urshr       v17.8h, v11.8h, #8
    raddhn      v28.8b, v14.8h, v8.8h
    raddhn      v29.8b, v15.8h, v9.8h
    raddhn      v30.8b, v16.8h, v10.8h
    raddhn      v31.8b, v17.8h, v11.8h
.endm


/******************************************************************************/

.macro pixman_composite_over_8888_8888_process_pixblock_head
    pixman_composite_out_reverse_8888_8888_process_pixblock_head
.endm

.macro pixman_composite_over_8888_8888_process_pixblock_tail
    pixman_composite_out_reverse_8888_8888_process_pixblock_tail
    uqadd       v28.8b, v0.8b, v28.8b
    uqadd       v29.8b, v1.8b, v29.8b
    uqadd       v30.8b, v2.8b, v30.8b
    uqadd       v31.8b, v3.8b, v31.8b
.endm

.macro pixman_composite_over_8888_8888_process_pixblock_tail_head
     ld4        {v4.8b, v5.8b, v6.8b, v7.8b}, [DST_R], #32
        urshr       v14.8h, v8.8h, #8
                                    PF add, PF_X, PF_X, #8
                                    PF tst, PF_CTL, #0xF
        urshr       v15.8h, v9.8h, #8
        urshr       v16.8h, v10.8h, #8
        urshr       v17.8h, v11.8h, #8
                                    PF beq, 10f
                                    PF add, PF_X, PF_X, #8
                                    PF sub, PF_CTL, PF_CTL, #1
10:
        raddhn      v28.8b, v14.8h, v8.8h
        raddhn      v29.8b, v15.8h, v9.8h
                                    PF cmp, PF_X, ORIG_W
        raddhn      v30.8b, v16.8h, v10.8h
        raddhn      v31.8b, v17.8h, v11.8h
        uqadd       v28.8b, v0.8b, v28.8b
        uqadd       v29.8b, v1.8b, v29.8b
        uqadd       v30.8b, v2.8b, v30.8b
        uqadd       v31.8b, v3.8b, v31.8b
    fetch_src_pixblock
                                    PF lsl, DUMMY, PF_X, #src_bpp_shift
                                    PF prfm, PREFETCH_MODE, [PF_SRC, DUMMY]
    mvn        v22.8b, v3.8b
                                    PF lsl, DUMMY, PF_X, #dst_bpp_shift
                                    PF prfm, PREFETCH_MODE, [PF_DST, DUMMY]
         st4        {v28.8b, v29.8b, v30.8b, v31.8b}, [DST_W], #32
                                    PF ble, 10f
                                    PF sub, PF_X, PF_X, ORIG_W
10:
    umull      v8.8h, v22.8b, v4.8b
                                    PF ble, 10f
                                    PF subs, PF_CTL, PF_CTL, #0x10
10:
    umull      v9.8h, v22.8b, v5.8b
                                    PF ble, 10f
                                    PF add, PF_SRC, PF_SRC, SRC_STRIDE, lsl #src_bpp_shift
                                    PF ldrsb, DUMMY, [PF_SRC]
10:
    umull      v10.8h, v22.8b, v6.8b
                                    PF ble, 10f
                                    PF add, PF_DST, PF_DST, DST_STRIDE, lsl #dst_bpp_shift
                                    PF ldrsb, DUMMY, [PF_DST]
10:
    umull      v11.8h, v22.8b, v7.8b
.endm

generate_composite_function \
    pixman_composite_over_8888_8888_asm_neon, 32, 0, 32, \
    FLAG_DST_READWRITE | FLAG_DEINTERLEAVE_32BPP, \
    8, /* number of pixels, processed in a single block */ \
    5, /* prefetch distance */ \
    default_init, \
    default_cleanup, \
    pixman_composite_over_8888_8888_process_pixblock_head, \
    pixman_composite_over_8888_8888_process_pixblock_tail, \
    pixman_composite_over_8888_8888_process_pixblock_tail_head

generate_composite_function_single_scanline \
    pixman_composite_scanline_over_asm_neon, 32, 0, 32, \
    FLAG_DST_READWRITE | FLAG_DEINTERLEAVE_32BPP, \
    8, /* number of pixels, processed in a single block */ \
    default_init, \
    default_cleanup, \
    pixman_composite_over_8888_8888_process_pixblock_head, \
    pixman_composite_over_8888_8888_process_pixblock_tail, \
    pixman_composite_over_8888_8888_process_pixblock_tail_head

/******************************************************************************/

.macro pixman_composite_over_n_8888_process_pixblock_head
    /* deinterleaved source pixels in {v0, v1, v2, v3} */
    /* inverted alpha in {v24} */
    /* destination pixels in {v4, v5, v6, v7} */
    umull       v8.8h, v24.8b, v4.8b
    umull       v9.8h, v24.8b, v5.8b
    umull       v10.8h, v24.8b, v6.8b
    umull       v11.8h, v24.8b, v7.8b
.endm

.macro pixman_composite_over_n_8888_process_pixblock_tail
    urshr       v14.8h, v8.8h, #8
    urshr       v15.8h, v9.8h, #8
    urshr       v16.8h, v10.8h, #8
    urshr       v17.8h, v11.8h, #8
    raddhn      v28.8b, v14.8h, v8.8h
    raddhn      v29.8b, v15.8h, v9.8h
    raddhn      v30.8b, v16.8h, v10.8h
    raddhn      v31.8b, v17.8h, v11.8h
    uqadd       v28.8b, v0.8b, v28.8b
    uqadd       v29.8b, v1.8b, v29.8b
    uqadd       v30.8b, v2.8b, v30.8b
    uqadd       v31.8b, v3.8b, v31.8b
.endm

.macro pixman_composite_over_n_8888_process_pixblock_tail_head
        urshr       v14.8h, v8.8h, #8
        urshr       v15.8h, v9.8h, #8
        urshr       v16.8h, v10.8h, #8
        urshr       v17.8h, v11.8h, #8
        raddhn      v28.8b, v14.8h, v8.8h
        raddhn      v29.8b, v15.8h, v9.8h
        raddhn      v30.8b, v16.8h, v10.8h
        raddhn      v31.8b, v17.8h, v11.8h
    ld4         {v4.8b, v5.8b, v6.8b, v7.8b}, [DST_R], #32
        uqadd       v28.8b, v0.8b, v28.8b
                                    PF add, PF_X, PF_X, #8
                                    PF tst, PF_CTL, #0x0F
                                    PF beq, 10f
                                    PF add, PF_X, PF_X, #8
                                    PF sub, PF_CTL, PF_CTL, #1
10:
        uqadd       v29.8b, v1.8b, v29.8b
        uqadd       v30.8b, v2.8b, v30.8b
        uqadd       v31.8b, v3.8b, v31.8b
                                    PF cmp, PF_X, ORIG_W
    umull       v8.8h, v24.8b, v4.8b
                                    PF lsl, DUMMY, PF_X, #dst_bpp_shift
                                    PF prfm, PREFETCH_MODE, [PF_DST, DUMMY]
    umull       v9.8h, v24.8b, v5.8b
                                    PF ble, 10f
                                    PF sub, PF_X, PF_X, ORIG_W
10:
    umull       v10.8h, v24.8b, v6.8b
                                    PF subs, PF_CTL, PF_CTL, #0x10
    umull       v11.8h, v24.8b, v7.8b
                                    PF ble, 10f
                                    PF add, PF_DST, PF_DST, DST_STRIDE, lsl #dst_bpp_shift
                                    PF ldrsb, DUMMY, [PF_DST]
10:
        st4         {v28.8b, v29.8b, v30.8b, v31.8b}, [DST_W], #32
.endm

.macro pixman_composite_over_n_8888_init
    mov         v3.s[0], w4
    dup         v0.8b, v3.b[0]
    dup         v1.8b, v3.b[1]
    dup         v2.8b, v3.b[2]
    dup         v3.8b, v3.b[3]
    mvn         v24.8b, v3.8b  /* get inverted alpha */
.endm

generate_composite_function \
    pixman_composite_over_n_8888_asm_neon, 0, 0, 32, \
    FLAG_DST_READWRITE | FLAG_DEINTERLEAVE_32BPP, \
    8, /* number of pixels, processed in a single block */ \
    5, /* prefetch distance */ \
    pixman_composite_over_n_8888_init, \
    default_cleanup, \
    pixman_composite_over_8888_8888_process_pixblock_head, \
    pixman_composite_over_8888_8888_process_pixblock_tail, \
    pixman_composite_over_n_8888_process_pixblock_tail_head

/******************************************************************************/

.macro pixman_composite_src_n_8888_process_pixblock_head
.endm

.macro pixman_composite_src_n_8888_process_pixblock_tail
.endm

.macro pixman_composite_src_n_8888_process_pixblock_tail_head
    st1         {v0.2s, v1.2s, v2.2s, v3.2s}, [DST_W], #32
.endm

.macro pixman_composite_src_n_8888_init
    mov         v0.s[0], w4
    dup         v3.2s, v0.s[0]
    dup         v2.2s, v0.s[0]
    dup         v1.2s, v0.s[0]
    dup         v0.2s, v0.s[0]
.endm

.macro pixman_composite_src_n_8888_cleanup
.endm

generate_composite_function \
    pixman_composite_src_n_8888_asm_neon, 0, 0, 32, \
    FLAG_DST_WRITEONLY, \
    8, /* number of pixels, processed in a single block */ \
    0, /* prefetch distance */ \
    pixman_composite_src_n_8888_init, \
    pixman_composite_src_n_8888_cleanup, \
    pixman_composite_src_n_8888_process_pixblock_head, \
    pixman_composite_src_n_8888_process_pixblock_tail, \
    pixman_composite_src_n_8888_process_pixblock_tail_head, \
    0, /* dst_w_basereg */ \
    0, /* dst_r_basereg */ \
    0, /* src_basereg   */ \
    0  /* mask_basereg  */

/******************************************************************************/

.macro pixman_composite_src_8888_8888_process_pixblock_head
.endm

.macro pixman_composite_src_8888_8888_process_pixblock_tail
.endm

.macro pixman_composite_src_8888_8888_process_pixblock_tail_head
    st1  {v0.2s, v1.2s, v2.2s, v3.2s}, [DST_W], #32
    fetch_src_pixblock
    cache_preload 8, 8
.endm

generate_composite_function \
    pixman_composite_src_8888_8888_asm_neon, 32, 0, 32, \
    FLAG_DST_WRITEONLY, \
    8, /* number of pixels, processed in a single block */ \
    10, /* prefetch distance */ \
    default_init, \
    default_cleanup, \
    pixman_composite_src_8888_8888_process_pixblock_head, \
    pixman_composite_src_8888_8888_process_pixblock_tail, \
    pixman_composite_src_8888_8888_process_pixblock_tail_head, \
    0, /* dst_w_basereg */ \
    0, /* dst_r_basereg */ \
    0, /* src_basereg   */ \
    0  /* mask_basereg  */

