package org.telegram.skin.me

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import org.telegram.skin.Me
import org.telegram.skin.NoDockActions
import org.telegram.skin.PreviewSkin
import org.telegram.skin.SkinPreviews
import org.telegram.skin.dock.DockOverlay
import org.telegram.skin.fakeDock
import org.telegram.skin.theme.Skin
import org.telegram.skin.ui.Glyph

private val FakeMeStrings = MeStrings(
    qr = "我的二维码",
    tools = "工具箱",
    settings = "设置",
    openProfile = "打开个人资料",
    switchAccounts = "切换账号",
)

@Composable
private fun fakeMe(): MeState = MeState(FakeMeStrings).apply {
    card = MeCard(Me, "林一舟", "@yizhou", "+86 138 **** 2046", "在线", online = true)
    otherAccounts = 2
    accountsHint = "另有 2 个账号，按住底部的「我」上滑可以快速切换"
    toggles = listOf(
        MeToggle(0, Glyph.Moon, "夜间模式", Skin.colors.isDark, "夜间模式"),
        MeToggle(1, Glyph.Globe, "代理", false, "代理"),
        MeToggle(2, Glyph.DataSaver, "省流量", true, "省流量，移动网络不自动下载"),
        MeToggle(3, Glyph.Quiet, "勿扰", false, "勿扰，暂停全部通知"),
    )
    shortcuts = listOf(
        MeRow(0, Glyph.Bookmark, "收藏"),
        MeRow(1, Glyph.Stories, "我的动态", action = MeRowAction(Glyph.Camera, "发动态")),
    )
    tools = listOf(
        MeTool(0, Glyph.Checklist, "消息中心"),
        MeTool(1, Glyph.Broadcast, "群发"),
        MeTool(2, Glyph.Rules, "通知规则"),
        MeTool(3, Glyph.ShieldCheck, "安全检查"),
        MeTool(4, Glyph.Gauge, "性能监控"),
        MeTool(5, Glyph.Grid, "全部功能"),
    )
    settings = listOf(
        MeRow(10, Glyph.Person, "账号"),
        MeRow(11, Glyph.Palette, "对话与外观"),
        MeRow(12, Glyph.Lock, "隐私与安全"),
        MeRow(13, Glyph.Bell, "通知与声音"),
        MeRow(14, Glyph.Database, "数据与存储"),
        MeRow(15, Glyph.Folder, "对话文件夹"),
        MeRow(16, Glyph.Devices, "设备", value = "3"),
        MeRow(17, Glyph.Leaf, "省电"),
        MeRow(18, Glyph.Language, "语言", value = "简体中文"),
        MeRow(19, Glyph.Dock, "整理底栏"),
        MeRow(20, Glyph.Gear, "更多设置"),
    )
}

private object NoMeActions : MeActions {
    override fun onCard() = Unit
    override fun onQr() = Unit
    override fun onAccounts() = Unit
    override fun onNotice(id: Int) = Unit
    override fun onToggle(id: Int, x: Float, y: Float) = Unit
    override fun onToggleDetails(id: Int) = Unit
    override fun onRow(id: Int) = Unit
    override fun onRowAction(id: Int) = Unit
    override fun onTool(id: Int) = Unit
}

@PreviewTest
@SkinPreviews
@Composable
fun MePagePreview() = PreviewSkin {
    Box(Modifier.fillMaxSize()) {
        MePage(fakeMe(), NoMeActions)
        DockOverlay(fakeDock(position = 3f), NoDockActions)
    }
}

@PreviewTest
@SkinPreviews
@Composable
fun MePageNoticePreview() = PreviewSkin {
    Box(Modifier.fillMaxSize()) {
        MePage(
            fakeMe().apply {
                otherAccounts = 0
                accountsHint = null
                card = card?.copy(name = "一个名字特别特别长、长到一行放不下的人", handle = "", status = "连接中…", online = false)
                notices = listOf(MeNotice(0, "你的账号已被冻结，点此了解详情", danger = true))
            },
            NoMeActions,
        )
        DockOverlay(fakeDock(position = 3f), NoDockActions)
    }
}
