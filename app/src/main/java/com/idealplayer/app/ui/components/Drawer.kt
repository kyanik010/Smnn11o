package com.idealplayer.app.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.idealplayer.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.idealplayer.app.core.designsystem.theme.A2Motion
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens
import com.idealplayer.app.ui.navigation.Routes
import timber.log.Timber
import kotlinx.coroutines.delay

private const val TV_DRAWER_LOG_TAG = "TvDrawer"
private const val TV_DRAWER_COLLAPSE_DELAY_MS = 180L
private const val TV_DRAWER_FALLBACK_FOCUS_DELAY_MS = 420L

data class DrawerItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

val tvDrawerItems @Composable get() = listOf(
    DrawerItem(Routes.HOME, stringResource(R.string.nav_home), Icons.Outlined.Home, Icons.Filled.Home),
    DrawerItem(Routes.SEARCH, stringResource(R.string.nav_search), Icons.Outlined.Search, Icons.Filled.Search),
    DrawerItem(Routes.LIVE_TV, stringResource(R.string.nav_live_tv), Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    DrawerItem(Routes.TV_GUIDE, stringResource(R.string.tv_guide), Icons.Outlined.DateRange, Icons.Filled.DateRange),
    DrawerItem(Routes.MOVIES, stringResource(R.string.nav_movies), Icons.Outlined.Movie, Icons.Filled.Movie),
    DrawerItem(Routes.SERIES, stringResource(R.string.nav_series), Icons.Outlined.Tv, Icons.Filled.Tv),
    DrawerItem(Routes.CONTINUE_WATCHING, stringResource(R.string.nav_continue_watching), Icons.Filled.History, Icons.Filled.History),
    DrawerItem(Routes.FAVORITES, stringResource(R.string.favorites), Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    DrawerItem(Routes.PLAYLISTS, stringResource(R.string.playlists), Icons.AutoMirrored.Filled.PlaylistPlay, Icons.AutoMirrored.Filled.PlaylistPlay),
    DrawerItem(Routes.SETTINGS, stringResource(R.string.nav_settings), Icons.Outlined.Settings, Icons.Filled.Settings)
)

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TV-specific side drawer
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun TvDrawerLayout(
    isExpanded: Boolean,
    selectedRoute: String,
    onToggle: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val items = tvDrawerItems
    val exitLabel = stringResource(R.string.nav_exit_playlist)
    val logoPainter = painterResource(id = R.drawable.idealplayer_logo)
    val exitItem = remember(exitLabel) {
        DrawerItem(
            Routes.EXIT,
            exitLabel,
            Icons.AutoMirrored.Outlined.ExitToApp,
            Icons.AutoMirrored.Filled.ExitToApp
        )
    }
    val menuItems = remember(items, exitItem) {
        items + exitItem
    }
    val menuRoutes = remember(menuItems) { menuItems.map { it.route } }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val itemFocusRequesters = remember(menuRoutes) {
        menuRoutes.associateWith { FocusRequester() }
    }
    val firstMenuRoute = menuRoutes.firstOrNull()
    val selectedMenuRoute = selectedRoute.takeIf { it in itemFocusRequesters } ?: firstMenuRoute
    var lastFocusedRoute by rememberSaveable {
        mutableStateOf(selectedMenuRoute ?: "")
    }
    var focusedIndex by rememberSaveable {
        mutableStateOf(menuRoutes.indexOf(selectedMenuRoute).takeIf { it >= 0 } ?: 0)
    }
    var focusedDrawerTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var remoteMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingDrawerFocusRoute by remember { mutableStateOf<String?>(null) }
    var drawerExpanded by rememberSaveable { mutableStateOf(isExpanded) }
    var layoutHasFocus by remember { mutableStateOf(false) }
    var hasRequestedFallbackFocus by remember { mutableStateOf(false) }
    val shouldExpandDrawer = isExpanded || remoteMenuExpanded || focusedDrawerTarget != null
    val drawerWidth by animateDpAsState(
        targetValue = if (drawerExpanded) dimens.drawerWidth else dimens.drawerCollapsedWidth,
        animationSpec = tween(durationMillis = A2Motion.StandardMillis, easing = FastOutSlowInEasing),
        label = "tvDrawerWidth"
    )

    fun requestDrawerFocus(route: String?) {
        pendingDrawerFocusRoute = route
            ?.takeIf { it in itemFocusRequesters }
            ?: selectedMenuRoute
            ?: firstMenuRoute
    }

    fun closeDrawerFromRemote(): Boolean {
        var handled = false
        if (remoteMenuExpanded) {
            remoteMenuExpanded = false
            handled = true
        }
        if (focusedDrawerTarget != null) {
            focusedDrawerTarget = null
            handled = true
        }
        if (isExpanded) {
            onToggle()
            handled = true
        }
        return handled
    }

    fun toggleDrawerFromRemote() {
        if (shouldExpandDrawer || drawerExpanded) {
            closeDrawerFromRemote()
        } else {
            remoteMenuExpanded = true
            requestDrawerFocus(lastFocusedRoute.takeIf { it in itemFocusRequesters } ?: selectedMenuRoute)
            onToggle()
        }
    }

    LaunchedEffect(menuRoutes, selectedRoute) {
        val fallbackRoute = selectedRoute.takeIf { it in itemFocusRequesters } ?: firstMenuRoute ?: ""
        if (lastFocusedRoute !in itemFocusRequesters) {
            lastFocusedRoute = fallbackRoute
        }
        if (focusedDrawerTarget == null) {
            lastFocusedRoute = fallbackRoute
        }
        if (focusedIndex !in menuRoutes.indices) {
            focusedIndex = menuRoutes.indexOf(lastFocusedRoute).takeIf { it >= 0 } ?: 0
        }
    }

    LaunchedEffect(isExpanded, selectedMenuRoute) {
        if (isExpanded) {
            requestDrawerFocus(lastFocusedRoute.takeIf { it in itemFocusRequesters } ?: selectedMenuRoute)
        }
    }

    // Content screens normally establish their own first TV focus target. Loading, empty and
    // error states often have no actionable child, though; without a fallback the app receives
    // no D-pad events at all. Give the selected drawer item focus only after content had a
    // chance to attach, and only once for this shell instance.
    LaunchedEffect(selectedMenuRoute) {
        if (!hasRequestedFallbackFocus) {
            delay(TV_DRAWER_FALLBACK_FOCUS_DELAY_MS)
            if (!layoutHasFocus) {
                selectedMenuRoute?.let { route ->
                    val routeIndex = menuRoutes.indexOf(route)
                    val requester = itemFocusRequesters[route]
                    if (routeIndex >= 0 && requester != null) {
                        listState.scrollAndRequestFocusSafely(
                            index = routeIndex,
                            requester = requester,
                            reason = "TV drawer fallback focus $route"
                        )
                    }
                }
            }
            hasRequestedFallbackFocus = true
        }
    }

    // Navigation Compose can restore focus onto the matching rail item when a new top-level
    // destination installs its drawer. Recover once during that drawer's initial composition;
    // later, intentional user focus inside the rail is left untouched.
    LaunchedEffect(selectedRoute) {
        delay(260)
        if (!isExpanded && focusedDrawerTarget == selectedRoute) {
            runCatching { focusManager.moveFocus(FocusDirection.Right) }
        }
    }

    LaunchedEffect(shouldExpandDrawer) {
        if (shouldExpandDrawer) {
            drawerExpanded = true
        } else {
            delay(TV_DRAWER_COLLAPSE_DELAY_MS)
            if (!shouldExpandDrawer) {
                drawerExpanded = false
            }
        }
    }

    LaunchedEffect(drawerExpanded, pendingDrawerFocusRoute, menuRoutes) {
        val route = pendingDrawerFocusRoute ?: return@LaunchedEffect
        if (!drawerExpanded) return@LaunchedEffect

        delay(80)
        val routeIndex = menuRoutes.indexOf(route)
        val requester = itemFocusRequesters[route]
        if (routeIndex >= 0 && requester != null) {
            listState.scrollAndRequestFocusSafely(
                index = routeIndex,
                requester = requester,
                reason = "TV drawer remote focus $route"
            )
        }
        pendingDrawerFocusRoute = null
    }

    LaunchedEffect(drawerExpanded, focusedIndex) {
        if (!drawerExpanded || focusedIndex !in menuRoutes.indices) return@LaunchedEffect

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@LaunchedEffect

        val firstVisibleIndex = visibleItems.first().index
        val lastVisibleIndex = visibleItems.last().index
        if (focusedIndex < firstVisibleIndex || focusedIndex > lastVisibleIndex) {
            listState.scrollToItem(focusedIndex)
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(IdealPlayerColors.Background)
            .onFocusChanged { layoutHasFocus = it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        // Keep right-D-pad handling inside Compose. A drawer item has no fixed
                        // content requester because every route supplies a different content
                        // tree, so ask the focus manager for the nearest valid right-hand target.
                        // Consuming the event when no target exists is deliberate: otherwise an
                        // unhandled event can escape to the Activity on some TV launchers.
                        if (focusedDrawerTarget != null) {
                            runCatching { focusManager.moveFocus(FocusDirection.Right) }
                                .onFailure { error ->
                                    Timber.tag(TV_DRAWER_LOG_TAG)
                                        .w(error, "Unable to move focus from drawer to content")
                                }
                            true
                        } else {
                            false
                        }
                    }

                    AndroidKeyEvent.KEYCODE_MENU -> {
                        // A held Menu key emits repeat KeyDowns. Toggle once and consume the
                        // repeats so a single press cannot immediately reopen/close the drawer.
                        if (event.nativeKeyEvent.repeatCount == 0) {
                            toggleDrawerFromRemote()
                        }
                        true
                    }

                    AndroidKeyEvent.KEYCODE_BACK -> {
                        // Do not let an auto-repeated Back escape to Activity after the first
                        // event closed the drawer.
                        if (event.nativeKeyEvent.repeatCount > 0) {
                            true
                        } else if (shouldExpandDrawer || drawerExpanded) {
                            closeDrawerFromRemote()
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            }
    ) {
        Column(
            modifier = Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .background(IdealPlayerColors.Surface)
                .padding(
                    vertical = if (drawerExpanded) 16.dp else 18.dp,
                    horizontal = if (drawerExpanded) 10.dp else 8.dp
                )
        ) {
            TvDrawerBrand(
                isExpanded = drawerExpanded,
                logoPainter = logoPainter
            )

            Spacer(modifier = Modifier.height(if (drawerExpanded) 16.dp else 12.dp))
            HorizontalDivider(color = IdealPlayerColors.DividerColor, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(if (drawerExpanded) 10.dp else 12.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, item -> item.route }
                ) { index, item ->
                    TvDrawerItemRow(
                        item = item,
                        isSelected = selectedRoute == item.route,
                        isExpanded = drawerExpanded,
                        focusRequester = itemFocusRequesters.getValue(item.route),
                        trapUp = index == 0,
                        trapDown = false,
                        onFocusChanged = { isFocused ->
                            if (isFocused) {
                                focusedDrawerTarget = item.route
                                lastFocusedRoute = item.route
                                focusedIndex = index
                            } else if (focusedDrawerTarget == item.route) {
                                focusedDrawerTarget = null
                            }
                        },
                        onClick = {
                            if (item.route == selectedRoute) {
                                runCatching { focusManager.moveFocus(FocusDirection.Right) }
                            } else {
                                // The destination owns its first-focus policy. Releasing the old
                                // rail node prevents focus restoration from pinning the new route
                                // under an expanded drawer.
                                focusManager.clearFocus(force = true)
                                onNavigate(item.route)
                            }
                        }
                    )
                }

                item(key = Routes.EXIT) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = IdealPlayerColors.DividerColor, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    TvDrawerItemRow(
                        item = exitItem,
                        isSelected = false,
                        isExpanded = drawerExpanded,
                        focusRequester = itemFocusRequesters.getValue(Routes.EXIT),
                        trapUp = items.isEmpty(),
                        trapDown = true,
                        onFocusChanged = { isFocused ->
                            if (isFocused) {
                                focusedDrawerTarget = Routes.EXIT
                                lastFocusedRoute = Routes.EXIT
                                focusedIndex = items.lastIndex + 1
                            } else if (focusedDrawerTarget == Routes.EXIT) {
                                focusedDrawerTarget = null
                            }
                        },
                        onClick = { onNavigate(Routes.EXIT) }
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier
                .width(dimens.drawerGutter)
                .fillMaxHeight()
                .background(IdealPlayerColors.Background)
        )

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            content()
        }
    }
}

@Composable
private fun TvDrawerBrand(
    isExpanded: Boolean,
    logoPainter: androidx.compose.ui.graphics.painter.Painter
) {
    val containerColor by animateColorAsState(
        targetValue = if (isExpanded) {
            IdealPlayerColors.SurfaceVariant.copy(alpha = 0.36f)
        } else {
            IdealPlayerColors.SurfaceVariant.copy(alpha = 0.24f)
        },
        animationSpec = tween(180),
        label = "tvDrawerBrandBackground"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .padding(
                horizontal = if (isExpanded) 14.dp else 10.dp,
                vertical = if (isExpanded) 14.dp else 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center
    ) {
        Image(
            painter = logoPainter,
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(if (isExpanded) 62.dp else 36.dp)
        )
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(160)) + expandHorizontally(animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(120)) + shrinkHorizontally(animationSpec = tween(120))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = IdealPlayerColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun FocusRequester.requestFocusSafely(reason: String) {
    runCatching { requestFocus() }
        .onFailure { error ->
            Timber.tag(TV_DRAWER_LOG_TAG).w(error, "Unable to request focus for %s", reason)
        }
}

private suspend fun LazyListState.scrollAndRequestFocusSafely(
    index: Int,
    requester: FocusRequester,
    reason: String
) {
    // A LazyColumn owns only the visible focus nodes. Scroll first so programmatic focus never
    // points at a requester whose item has not been composed yet.
    scrollToItem(index)
    withFrameNanos { }
    requester.requestFocusSafely(reason)
}

@Composable
private fun CollapsedDrawerPlate(
    item: DrawerItem,
    isSelected: Boolean,
    isFocused: Boolean
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) A2Motion.FocusScale else 1f,
        animationSpec = tween(A2Motion.StandardMillis, easing = FastOutSlowInEasing),
        label = "collapseScale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> IdealPlayerColors.SurfaceFocus
            isSelected -> IdealPlayerColors.SurfaceSelected
            else -> IdealPlayerColors.CardBackground
        },
        animationSpec = tween(A2Motion.StandardMillis, easing = FastOutSlowInEasing),
        label = "collapseBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused || isSelected -> IdealPlayerColors.TextPrimary
            item.route == Routes.EXIT -> IdealPlayerColors.Error
            else -> IdealPlayerColors.TextSecondary
        },
        animationSpec = tween(A2Motion.StandardMillis, easing = FastOutSlowInEasing),
        label = "collapseContent"
    )

    val itemShape = RoundedCornerShape(14.dp)
    val borderWidth = when {
        isFocused -> 4.dp
        isSelected -> 2.dp
        else -> 1.dp
    }
    val borderColor = when {
        isFocused -> IdealPlayerColors.FocusBorder
        isSelected -> IdealPlayerColors.SelectedBorder
        else -> IdealPlayerColors.CardBorder
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shape = itemShape
                clip = false
            }
            .clip(itemShape)
            .background(bgColor)
            .border(borderWidth, borderColor, itemShape),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp)
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(IdealPlayerColors.Secondary)
            )
        }
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun ExpandedDrawerPlate(
    item: DrawerItem,
    isSelected: Boolean,
    isFocused: Boolean
) {
    val plateShape = RoundedCornerShape(14.dp)

    val scale by animateFloatAsState(
        targetValue = if (isFocused) A2Motion.FocusScale else 1f,
        animationSpec = tween(A2Motion.StandardMillis, easing = FastOutSlowInEasing),
        label = "expandScale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> IdealPlayerColors.SurfaceFocus
            isSelected -> IdealPlayerColors.SurfaceSelected
            else -> IdealPlayerColors.CardBackground
        },
        animationSpec = tween(A2Motion.StandardMillis, easing = FastOutSlowInEasing),
        label = "expandBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused || isSelected -> IdealPlayerColors.TextPrimary
            item.route == Routes.EXIT -> IdealPlayerColors.Error
            else -> IdealPlayerColors.TextSecondary
        },
        animationSpec = tween(A2Motion.StandardMillis, easing = FastOutSlowInEasing),
        label = "expandContent"
    )

    val borderWidth = when {
        isFocused -> 4.dp
        isSelected -> 2.dp
        else -> 1.dp
    }
    val borderColor = when {
        isFocused -> IdealPlayerColors.FocusBorder
        isSelected -> IdealPlayerColors.SelectedBorder
        else -> IdealPlayerColors.CardBorder
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shape = plateShape
                clip = false
            }
            .clip(plateShape)
            .background(bgColor)
            .border(borderWidth, borderColor, plateShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .padding(start = 2.dp)
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(IdealPlayerColors.Secondary)
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 12.dp, end = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.icon,
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )
        }

        Text(
            text = item.label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(16.dp))
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvDrawerItemRow(
    item: DrawerItem,
    isSelected: Boolean,
    isExpanded: Boolean,
    focusRequester: FocusRequester,
    trapUp: Boolean,
    trapDown: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }

    // Invisible shell that securely connects DPAD interaction states without enforcing layout shape limitations.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .zIndex(if (isFocused) 1f else 0f)
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .focusProperties {
                left = FocusRequester.Cancel
                // Interior movement must use LazyColumn's beyond-bounds search. An explicit
                // requester for an off-screen neighbor is detached and throws from focusSearch.
                if (trapUp) up = FocusRequester.Cancel
                if (trapDown) down = FocusRequester.Cancel
            }
            .focusRequester(focusRequester)
            .testTag("tv_drawer_item_${item.route}")
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = isSelected
            },
        contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center
    ) {
        if (isExpanded) {
            ExpandedDrawerPlate(
                item = item,
                isSelected = isSelected,
                isFocused = isFocused
            )
        } else {
            CollapsedDrawerPlate(
                item = item,
                isSelected = isSelected,
                isFocused = isFocused
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Mobile bottom navigation and tablet navigation rail
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

val bottomNavItems @Composable get() = listOf(
    DrawerItem(Routes.HOME, stringResource(R.string.nav_home), Icons.Outlined.Home, Icons.Filled.Home),
    DrawerItem(Routes.LIVE_TV, stringResource(R.string.nav_live_tv), Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    DrawerItem(Routes.MOVIES, stringResource(R.string.nav_movies), Icons.Outlined.Movie, Icons.Filled.Movie),
    DrawerItem(Routes.SERIES, stringResource(R.string.nav_series), Icons.Outlined.Tv, Icons.Filled.Tv),
    DrawerItem(Routes.FAVORITES, stringResource(R.string.favorites), Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite)
)

val tabletRailItems @Composable get() = listOf(
    DrawerItem(Routes.HOME, stringResource(R.string.nav_home), Icons.Outlined.Home, Icons.Filled.Home),
    DrawerItem(Routes.LIVE_TV, stringResource(R.string.nav_live_tv), Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    DrawerItem(Routes.MOVIES, stringResource(R.string.nav_movies), Icons.Outlined.Movie, Icons.Filled.Movie),
    DrawerItem(Routes.SERIES, stringResource(R.string.nav_series), Icons.Outlined.Tv, Icons.Filled.Tv),
    DrawerItem(Routes.TV_GUIDE, stringResource(R.string.tv_guide), Icons.Outlined.DateRange, Icons.Filled.DateRange),
    DrawerItem(Routes.SEARCH, stringResource(R.string.nav_search), Icons.Outlined.Search, Icons.Filled.Search),
    DrawerItem(Routes.FAVORITES, stringResource(R.string.favorites), Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    DrawerItem(Routes.SETTINGS, stringResource(R.string.nav_settings), Icons.Outlined.Settings, Icons.Filled.Settings)
)

@Composable
fun MobileScaffoldLayout(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600

    if (isTablet) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(IdealPlayerColors.Background)
        ) {
            TabletNavigationRail(
                selectedRoute = selectedRoute,
                onNavigate = onNavigate
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                content(PaddingValues())
            }
        }
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = IdealPlayerColors.Background,
            bottomBar = {
                MobileBottomNavigation(
                    selectedRoute = selectedRoute,
                    onNavigate = onNavigate
                )
            }
        ) { paddingValues ->
            content(paddingValues)
        }
    }
}

@Composable
private fun MobileBottomNavigation(
    selectedRoute: String,
    onNavigate: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .background(IdealPlayerColors.SurfaceElevated)
            .border(
                width = 1.dp,
                color = IdealPlayerColors.CardBorder,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        bottomNavItems.forEach { item ->
            val isSelected = selectedRoute == item.route
            val iconColor by animateColorAsState(
                targetValue = if (isSelected) IdealPlayerColors.Secondary else IdealPlayerColors.TextSecondary,
                animationSpec = tween(A2Motion.FastMillis),
                label = "mobileNavIconColor"
            )
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) IdealPlayerColors.SurfaceSelected else Color.Transparent,
                animationSpec = tween(A2Motion.FastMillis),
                label = "mobileNavBackground"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .clickable(
                        role = Role.Tab,
                        onClick = { onNavigate(item.route) }
                    )
                    .semantics(mergeDescendants = true) {
                        role = Role.Tab
                        selected = isSelected
                    }
                    .testTag("mobile_bottom_nav_${item.route}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.label,
                    color = iconColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TabletNavigationRail(
    selectedRoute: String,
    onNavigate: (String) -> Unit
) {
    val items = tabletRailItems

    Column(
        modifier = Modifier
            .width(80.dp)
            .fillMaxHeight()
            .background(IdealPlayerColors.Surface)
            .border(
                width = 1.dp,
                color = IdealPlayerColors.CardBorder,
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        items.forEachIndexed { index, item ->
            if (index == items.lastIndex) {
                Spacer(modifier = Modifier.weight(1f))
            }
            TabletNavigationItem(
                item = item,
                isSelected = selectedRoute == item.route,
                onClick = { onNavigate(item.route) }
            )
            if (index < items.lastIndex - 1) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun TabletNavigationItem(
    item: DrawerItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) IdealPlayerColors.Secondary else IdealPlayerColors.TextSecondary,
        animationSpec = tween(A2Motion.FastMillis),
        label = "tabletRailContent"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) IdealPlayerColors.SurfaceSelected else Color.Transparent,
        animationSpec = tween(A2Motion.FastMillis),
        label = "tabletRailBackground"
    )

    Column(
        modifier = Modifier
            .size(width = 64.dp, height = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, IdealPlayerColors.SelectedBorder, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = isSelected
            }
            .testTag("tablet_rail_${item.route}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = item.label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Backward-compatible wrapper 
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Unified wrapper: TV gets side drawer, Mobile gets bottom navigation.
 * Called by all screens that need the standard shell.
 */
@Composable
fun IdealPlayerDrawer(
    isExpanded: Boolean,
    selectedRoute: String,
    isTv: Boolean,
    onToggle: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (isTv) {
        TvDrawerLayout(
            isExpanded = isExpanded,
            selectedRoute = selectedRoute,
            onToggle = onToggle,
            onNavigate = onNavigate,
            modifier = modifier,
            content = content
        )
    } else {
        // For mobile, this just provides content directly.
        // The BottomNavigation is handled by the Navigation scaffold.
        // Individual screens still call IdealPlayerDrawer but on mobile it's a pass-through.
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(IdealPlayerColors.Background)
        ) {
            content()
        }
    }
}
