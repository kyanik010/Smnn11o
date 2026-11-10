package com.idealplayer.app.ui.components.a2

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.idealplayer.app.R

private val A2ArtworkResources = intArrayOf(
    R.drawable.a2_artwork_coast,
    R.drawable.a2_artwork_city,
    R.drawable.a2_artwork_documentary
)

@DrawableRes
internal fun a2ArtworkResource(seed: String): Int =
    A2ArtworkResources[seed.hashCode().and(Int.MAX_VALUE) % A2ArtworkResources.size]

/** Exact synthetic artwork variants exported from the approved A2 Figma component. */
@Composable
fun A2ArtworkFallback(
    seed: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop
) {
    val resource = remember(seed) { a2ArtworkResource(seed) }

    Image(
        painter = painterResource(resource),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}
