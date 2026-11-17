package org.telegram.skin.dock

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.telegram.skin.NoDockActions
import org.telegram.skin.OverPage
import org.telegram.skin.PreviewSkin
import org.telegram.skin.SkinPreviews
import org.telegram.skin.fakeDock
import org.telegram.skin.ui.Peer

// The dock in each of its states, floated over a chat list.

@PreviewTest
@SkinPreviews
@Composable
fun DockRestPreview() = PreviewSkin { OverPage { DockOverlay(fakeDock(), NoDockActions) } }

@PreviewTest
@SkinPreviews
@Composable
fun DockFoldedPreview() = PreviewSkin {
    OverPage { DockOverlay(fakeDock().apply { collapsed = true }, NoDockActions) }
}

@PreviewTest
@SkinPreviews
@Composable
fun DockCreatePreview() = PreviewSkin { OverPage { DockOverlay(fakeDock(DockMode.Create), NoDockActions) } }

@PreviewTest
@SkinPreviews
@Composable
fun DockAccountsPreview() = PreviewSkin {
    OverPage { DockOverlay(fakeDock(DockMode.Accounts, position = 3f), NoDockActions, restingPick = 1) }
}

/** A frame of the cards being dealt out of the 我 key, a little under halfway. */
@PreviewTest
@SkinPreviews
@Composable
fun DockDealingPreview() = PreviewSkin {
    OverPage { DockOverlay(fakeDock(DockMode.Accounts, position = 3f), NoDockActions, restingDeal = 0.45f) }
}

@PreviewTest
@SkinPreviews
@Composable
fun DockArrangePreview() = PreviewSkin { OverPage { DockOverlay(fakeDock(DockMode.Arrange, position = 1f), NoDockActions) } }

// Edge cases: more accounts than the fan has room for, and a search with results.

@PreviewTest
@SkinPreviews
@Composable
fun DockManyAccountsPreview() = PreviewSkin {
    val state = fakeDock(DockMode.Accounts, position = 3f).apply {
        accounts = accounts + listOf("周周", "阿杰工作号", "客服一号", "客服二号", "备用号", "旧手机号").mapIndexed { i, name ->
            DockAccount(3 + i, Peer(3 + i, 7100L + i, name), name, "@spare$i", if (i % 2 == 0) "${i + 1} 条未读" else "", current = false)
        }
    }
    OverPage { DockOverlay(state, NoDockActions) }
}

@PreviewTest
@SkinPreviews
@Composable
fun DockSearchPreview() = PreviewSkin {
    val state = fakeDock(DockMode.Create).apply {
        query = "王"
        results = listOf(
            DockContact(Peer(0, 300, "王老师"), "王老师", online = true, subtitle = "@teacher_wang"),
            DockContact(Peer(0, 301, "王小明"), "王小明", online = false),
            DockContact(Peer(0, 302, "王氏宗亲会"), "王氏宗亲会", online = false, subtitle = "@wang_family"),
        )
        searching = true
    }
    OverPage { DockOverlay(state, NoDockActions) }
}
