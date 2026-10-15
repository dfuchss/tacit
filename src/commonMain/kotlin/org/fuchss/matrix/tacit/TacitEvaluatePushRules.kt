package org.fuchss.matrix.tacit

import de.connect2x.trixnity.client.notification.EvaluatePushRules
import de.connect2x.trixnity.client.notification.EvaluatePushRulesImpl
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.TombstoneEventContent
import de.connect2x.trixnity.core.model.push.PushAction
import de.connect2x.trixnity.core.model.push.PushRule
import org.koin.dsl.module

// State events match "all messages" push rules and would otherwise inflate unread counts and trigger OS notifications.
class TacitEvaluatePushRules(
    private val delegate: EvaluatePushRules,
    private val userInfo: UserInfo,
) : EvaluatePushRules {
    override suspend fun invoke(event: ClientEvent<*>, allRules: List<PushRule>): Set<PushAction>? {
        if (event is ClientEvent.StateBaseEvent<*> && !event.isNotifiableStateEvent()) return null
        return delegate(event, allRules)
    }

    private fun ClientEvent.StateBaseEvent<*>.isNotifiableStateEvent(): Boolean = when (val content = content) {
        is MemberEventContent -> content.membership == Membership.INVITE && stateKey == userInfo.userId.full
        is TombstoneEventContent -> true
        else -> false
    }
}

fun tacitClientModule() = module {
    single<EvaluatePushRules> { TacitEvaluatePushRules(get<EvaluatePushRulesImpl>(), get()) }
}
