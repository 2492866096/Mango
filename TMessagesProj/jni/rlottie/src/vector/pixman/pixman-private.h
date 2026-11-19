#ifndef PIXMAN_PRIVATE_H
#define PIXMAN_PRIVATE_H

/*
 * The defines which are shared between C and assembly code
 */

/* bilinear interpolation precision (must be < 8) */
#define BILINEAR_INTERPOLATION_BITS 7
#define BILINEAR_INTERPOLATION_RANGE (1 << BILINEAR_INTERPOLATION_BITS)

/*
 * C specific part
 */


/* Only the part shared with the assembler is kept here. */

#endif /* PIXMAN_PRIVATE_H */
