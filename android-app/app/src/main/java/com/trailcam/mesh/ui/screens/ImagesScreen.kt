package com.trailcam.mesh.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.trailcam.mesh.data.CapturedImage
import com.trailcam.mesh.data.ImageSettings
import com.trailcam.mesh.ui.theme.TrailCamDimens
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ImagesScreen(
    images: List<CapturedImage>,
    selectedImage: CapturedImage?,
    nodeImageSettings: Map<Int, ImageSettings>,
    nodeNames: Map<Int, String>,
    onImageClick: (CapturedImage) -> Unit,
    onDismissImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (images.isEmpty()) {
        EmptyImagesState(modifier)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = TrailCamDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(TrailCamDimens.ContentSpacingSmall),
            horizontalArrangement = Arrangement.spacedBy(TrailCamDimens.ContentSpacingSmall),
            contentPadding = PaddingValues(vertical = TrailCamDimens.ContentSpacingMedium)
        ) {
            items(images, key = { "${it.nodeId}-${it.imageId}" }) { image ->
                ImageCard(
                    image = image,
                    imageSettings = nodeImageSettings[image.nodeId] ?: ImageSettings(),
                    nodeName = nodeNames[image.nodeId] ?: "Camera ${image.nodeId}",
                    onClick = { onImageClick(image) }
                )
            }
        }
    }
    
    // Full screen image dialog
    selectedImage?.let { image ->
        ImageDetailDialog(
            image = image,
            imageSettings = nodeImageSettings[image.nodeId] ?: ImageSettings(),
            nodeName = nodeNames[image.nodeId] ?: "Camera ${image.nodeId}",
            onDismiss = onDismissImage
        )
    }
}

@Composable
private fun ImageCard(
    image: CapturedImage,
    imageSettings: ImageSettings,
    nodeName: String,
    onClick: () -> Unit
) {
    val bitmap = remember(image.data, imageSettings) {
        try {
            val original = BitmapFactory.decodeByteArray(image.data, 0, image.data.size)
            if (original != null) {
                applyImageTransformations(original, imageSettings)?.asImageBitmap()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(TrailCamDimens.CardCornerRadius))
            .clickable(onClick = onClick)
    ) {
        Box {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Trail camera image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Overlay with info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    )
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = nodeName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateFormat.format(Date(image.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageDetailDialog(
    image: CapturedImage,
    imageSettings: ImageSettings,
    nodeName: String,
    onDismiss: () -> Unit
) {
    val bitmap = remember(image.data, imageSettings) {
        try {
            val original = BitmapFactory.decodeByteArray(image.data, 0, image.data.size)
            if (original != null) {
                applyImageTransformations(original, imageSettings)?.asImageBitmap()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    val dateFormat = remember { SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm:ss", Locale.getDefault()) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = nodeName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = dateFormat.format(Date(image.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                // Image
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Trail camera image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.BrokenImage,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Unable to load image",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Footer with image info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Size: ${image.data.size / 1024} KB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ID: ${image.imageId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Apply image transformations (flip/mirror) based on settings
 */
private fun applyImageTransformations(bitmap: Bitmap, settings: ImageSettings): Bitmap? {
    if (!settings.vflip && !settings.hmirror) {
        return bitmap
    }
    
    val matrix = Matrix()
    
    if (settings.vflip) {
        // Vertical flip: scale y by -1, pivot at center
        matrix.postScale(1f, -1f, bitmap.width / 2f, bitmap.height / 2f)
    }
    
    if (settings.hmirror) {
        // Horizontal mirror: scale x by -1, pivot at center
        matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
    }
    
    return try {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: Exception) {
        bitmap
    }
}

@Composable
private fun EmptyImagesState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Images Yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Captured images will appear here when motion is detected",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}


