package com.yandex.yatagan.core.graph

import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Graph-level abstraction over [MembersInjectorModel][com.yandex.yatagan.core.MembersInjectorModel].
 */
public interface GraphMemberInjector : MayBeInvalid {
    /**
     * See [injector][com.yandex.yatagan.core.MembersInjectorModel.injector]
     */
    public val injector: Method

    /**
     * See [membersToInject][com.yandex.yatagan.core.MembersInjectorModel.membersToInject]
     */
    public val membersToInject: Map<out Member, NodeDependency>
}