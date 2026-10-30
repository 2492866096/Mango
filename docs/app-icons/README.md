# 六款应用图标

本目录 `source/` 保存用户于 2026-10-03 提供的原始 JPG，按原字节复制，未进行 AI 重绘。

| 图标 | 原图文件 | 桌面资源 | 设置页去边预览 |
| --- | --- | --- | --- |
| 星空（默认） | `2026-10-03 08.35.52.jpg` | `ic_launcher` | `mango_icon_galaxy` |
| 粉色 | `2026-10-03 08.35.43.jpg` | `icon_6_launcher` | `mango_icon_pink` |
| 黑金 | `2026-10-03 08.36.00.jpg` | `icon_4_launcher` | `mango_icon_gold` |
| 月夜 | `2026-10-03 08.35.55.jpg` | `icon_3_launcher` | `mango_icon_moon` |
| 极速 | `2026-10-03 08.35.57.jpg` | `icon_5_launcher` | `mango_icon_speed` |
| 霓虹 | `2026-10-03 08.36.05.jpg` | `icon_2_launcher` | `mango_icon_neon` |

## 适配方式

- `mipmap-mdpi` 至 `mipmap-xxxhdpi`：传统桌面和圆形图标分别为 48、72、96、144、192 px。
- 自适应图标前景：108 dp 透明画布，原图缩放为 64 dp 居中；对应五档像素为 108、162、216、324、432。
- 自适应普通与 round XML 共用同一前景及纹理背景，由系统应用实际圆形/圆角矩形遮罩；不再叠加旧飞机前景或裁剪层。
- 新增 `mipmap-*/icon[_N]_background_texture.png`，五档分辨率与前景一致；`drawable/icon[_N]_background.xml` 作为 bitmap wrapper 引用它。背景由各自原图内部边缘纹理延展并柔化生成，不是新增纯黑/纯色厚外框。
- 采用 64 dp 原图尺寸，将人物头部、身体和尾巴保持在中央安全区域内；系统遮罩可能切去装饰性边角，但不放大裁切人物。
- 对图片外围相连的浅色白底做透明化和去白边处理；再用平滑蒙版柔化原图外围方形边框，并保护人物区域。内部眼睛、牙齿和角色黑色描边等原图内容保留；黑金原画中的深色艺术背景不是需要删除的额外黑框。
- 设置页使用 `drawable-nodpi/mango_icon_*.png`，为去边处理后 384 × 384 RGBA 圆角成图，与桌面效果一致，不含白色方形底框。完整原图在 `source/` 保留。
- 系统联系人账户、通话和相关头像资源 `drawable-*/ic_launcher_dr.png` 同步默认星空图标。
- 已移除无引用的旧飞机前景、背景裁剪层及第 7/8/9 套旧图资源；旧 alias 名称由 Java/Manifest 迁移兼容处理。

## 重新生成与验证

在项目根目录执行（需要 Python 3 和 Pillow，不调用 Gradle，也不生成 APK）：

```sh
python3 Tools/generate_launcher_icons.py
python3 Tools/tests/run_launcher_icon_tests.py
```

生成器读取本目录原始 JPG，写入 Android 资源，并输出：

`build/previews/launcher-icons-contact-sheet.png`

该图是素材适配预览，包含精修设置页预览、圆形桌面和圆角桌面效果，不是实机截图。生成脚本只更新这六套资源，不清理其他资源。
