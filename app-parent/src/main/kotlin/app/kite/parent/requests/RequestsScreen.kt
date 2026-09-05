package app.kite.parent.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.BackHeader
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.family.FamilyMember

/**
 * Every open request in the family on one screen, grouped by child. «Главная» shows only the
 * selected child's requests, so this is where a second child's «дай ещё 15 минут» surfaces.
 */
@Composable
fun RequestsScreen(
    children: List<FamilyMember>,
    parents: List<FamilyMember>,
    controller: RequestsController,
    onOpenTasks: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val requests = controller.requests

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        BackHeader(title = "Запросы", onBack = onBack)
        Spacer(Modifier.height(20.dp))

        if (requests.isEmpty()) {
            Text(
                text = "Пока никто ничего не просит.",
                style = typography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            )
            return@Column
        }

        InsetGroupedList {
            children.forEach { child ->
                val forChild = controller.forChild(child.id)
                if (forChild.isEmpty()) return@forEach
                InsetGroup(header = child.displayName.ifBlank { "Ребёнок" }) {
                    forChild.forEach { request ->
                        custom {
                            RequestCard(
                                request = request,
                                busy = controller.busy == request.id,
                                askedFor = askedForLabel(request.targetMemberId, parents, controller.myMemberId),
                                onApprove = { minutes, scoped ->
                                    controller.resolve(request, approve = true, minutes = minutes, scopeToApp = scoped)
                                },
                                onDeny = { controller.resolve(request, approve = false) },
                                onOpenTasks = onOpenTasks,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

internal fun askedForLabel(targetMemberId: String?, parents: List<FamilyMember>, myMemberId: String?): String? = when {
    targetMemberId == null -> null
    targetMemberId == myMemberId -> "Просит именно вас"
    else -> parents.firstOrNull { it.id == targetMemberId }?.displayName?.takeIf { it.isNotBlank() }?.let { "Просит: $it" }
}
