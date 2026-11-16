package org.telegram.skin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.telegram.skin.dock.DockAccount
import org.telegram.skin.dock.DockActions
import org.telegram.skin.dock.DockChoice
import org.telegram.skin.dock.DockContact
import org.telegram.skin.dock.DockKey
import org.telegram.skin.dock.DockMode
import org.telegram.skin.dock.DockState
import org.telegram.skin.dock.DockStrings
import org.telegram.skin.dock.DockTab
import org.telegram.skin.theme.Skin
import org.telegram.skin.ui.Avatar
import org.telegram.skin.ui.Glyph
import org.telegram.skin.ui.Peer
import org.telegram.skin.ui.SkinText

// Stand-in data for the screenshot suite: one person with three accounts, a
// handful of chats and the strings as the app would show them in Chinese.

val FakeDockStrings = DockStrings(
    create = "新建",
    close = "关闭",
    searchHint = "搜索联系人、用户名",
    recent = "最近联系",
    noRecent = "还没有最近联系的人",
    searching = "正在搜索…",
    noResults = "没有找到",
    releaseToSwitch = "松手即切换到",
    releaseToAdd = "松手即添加账号",
    releaseToMore = "松手查看全部账号",
    slideToPick = "滑到要切换的账号",
    tapToPick = "轻点要切换的账号",
    addAccount = "添加账号",
    moreAccounts = "另有 %d 个账号",
    arrangeTitle = "整理底栏",
    arrangeHint = "中间最多放三个，按点选的先后排列",
    arrangeFull = "已经放满三个，先点掉一个",
    switchAccountAction = "切换账号",
    arrangeAction = "整理底栏",
)

val Me = Peer(0, 7001, "林一舟")

fun fakeDock(mode: DockMode = DockMode.Rail, position: Float = 0f): DockState = DockState(FakeDockStrings).apply {
    tabs = listOf(
        DockTab(0, Glyph.Chats, "对话", "对话，102 条未读", readout = "102"),
        DockTab(1, Glyph.Contacts, "联系人", "联系人", arrangeable = true),
        DockTab(2, Glyph.Calls, "通话", "通话", arrangeable = true),
        DockTab(3, Glyph.Chats, "我", "我，按住上滑可切换账号", avatar = Me),
    )
    this.position = position
    this.mode = mode
    keys = listOf(
        DockKey(0, Glyph.NewChat, "新对话"),
        DockKey(1, Glyph.NewGroup, "新群组"),
        DockKey(2, Glyph.Feed, "新频道"),
        DockKey(3, Glyph.Secret, "私密对话"),
    )
    recents = listOf("小雨", "阿杰", "林姐", "王老师", "妈妈", "周周").mapIndexed { i, name ->
        DockContact(Peer(0, 100L + i, name), name, online = i == 0)
    }
    accounts = listOf(
        DockAccount(0, Me, "林一舟", "@yizhou", "当前", current = true),
        DockAccount(1, Peer(1, 7002, "林经理"), "林经理", "@lin_work", "5 条未读", current = false),
        DockAccount(2, Peer(2, 7003, "夜猫子"), "夜猫子", "@night_owl", "21 条未读", current = false),
    )
    choices = listOf(
        DockChoice(1, Glyph.Contacts, "联系人"),
        DockChoice(2, Glyph.Calls, "通话"),
        DockChoice(5, Glyph.Checklist, "消息中心"),
    )
    chosen = listOf(1, 2)
}

object NoDockActions : DockActions {
    override fun onTab(index: Int) = Unit
    override fun onTabMenu(index: Int, x: Float, y: Float) = Unit
    override fun onModeChanged(mode: DockMode) = Unit
    override fun onKey(id: Int) = Unit
    override fun onQuery(query: String) = Unit
    override fun onContact(contact: DockContact) = Unit
    override fun onAccount(account: Int) = Unit
    override fun onAddAccount() = Unit
    override fun onMoreAccounts() = Unit
    override fun onCycleAccount(forward: Boolean) = Unit
    override fun onArrange(choices: List<Int>) = Unit
}

/** A plain chat list to float the dock over, so the shadows and the veil have something to fall on. */
@Composable
fun FakeChatList() {
    val c = Skin.colors
    val chats = listOf(
        "家人群" to "妈妈：周末回来吃饭吗？我包了饺子",
        "小雨" to "[照片] 昨天拍的晚霞",
        "阿杰" to "明早九点，老地方见",
        "设计讨论组" to "王老师：新底栏的样张发群里了",
        "林姐" to "收到，谢谢",
        "读书会" to "下期读《长安的荔枝》",
        "周周" to "哈哈哈哈",
        "快递柜" to "您的包裹已到 3 号柜",
        "王老师" to "周四交稿，别拖",
        "夜跑小队" to "今晚还跑吗",
        "物业通知" to "明日上午停水两小时",
        "阿杰" to "好",
    )
    Column(
        Modifier.fillMaxSize().background(c.page).padding(horizontal = Skin.space.gutter, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SkinText("对话", Skin.type.display, c.text)
        chats.forEachIndexed { i, (name, preview) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(Peer(0, 200L + i, name), 50.dp)
                Column(Modifier.padding(start = 14.dp).fillMaxWidth()) {
                    SkinText(name, Skin.type.name, c.text, maxLines = 1)
                    SkinText(preview, Skin.type.preview, c.textSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun OverPage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        FakeChatList()
        content()
    }
}
