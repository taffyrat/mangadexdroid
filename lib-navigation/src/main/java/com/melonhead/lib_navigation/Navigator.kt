package com.melonhead.lib_navigation

import android.content.Context
import android.content.Intent
import com.melonhead.lib_navigation.keys.ActivityKey
import com.melonhead.lib_navigation.resolvers.ActivityResolver
import com.melonhead.lib_navigation.resolvers.ResolverMap

interface Navigator {
    fun <T: ActivityKey> intentForKey(context: Context, activityKey: T): Intent
}

internal class NavigatorImpl(
    private val resolverMap: ResolverMap,
): Navigator {
    override fun <T: ActivityKey> intentForKey(context: Context, activityKey: T): Intent {
        val resolver = resolverMap.activityResolvers[activityKey::class.java]
            ?: throw IllegalArgumentException("ActivityResolver not registered for $activityKey")
        val activityResolver = (resolver as? ActivityResolver<T>) ?: throw IllegalArgumentException("Incorrect type registered for $activityKey, expecting ActivityResolver<${activityKey::class.java}>, got ${resolver::class.java}")
        return activityResolver.intentForKey(context, activityKey)
    }
}
