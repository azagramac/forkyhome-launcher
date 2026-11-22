package dev.azagra.forkyhome.ui

import android.app.role.RoleManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.azagra.forkyhome.R
import kotlin.math.roundToInt

@Composable
fun OnboardingOverlay(
    dockBoundsInRoot: Rect,
    gridBoundsInRoot: Rect,
    onRestoreBackup: () -> Unit = {},
    onFinish: () -> Unit,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    var step by remember { mutableIntStateOf(0) }

    val roleManager = remember { ctx.getSystemService(RoleManager::class.java) }
    var isDefaultHome by remember { mutableStateOf(roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultHome = roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
    }

    val ringColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val padPx = with(density) { 8.dp.toPx() }

        val targetHighlight = when (step) {
            0 -> {
                if (dockBoundsInRoot.width > 0f && dockBoundsInRoot.height > 0f) {
                    dockBoundsInRoot.inflate(padPx)
                } else {
                    Rect(wPx * 0.08f, hPx * 0.84f, wPx * 0.92f, hPx * 0.95f)
                }
            }
            1 -> {
                if (gridBoundsInRoot.width > 0f && gridBoundsInRoot.height > 0f) {
                    val cx = gridBoundsInRoot.center.x
                    val cy = gridBoundsInRoot.top + gridBoundsInRoot.height * 0.35f
                    val r = minOf(gridBoundsInRoot.width, gridBoundsInRoot.height) * 0.28f
                    Rect(cx - r, cy - r, cx + r, cy + r)
                } else {
                    Rect(wPx * 0.20f, hPx * 0.25f, wPx * 0.80f, hPx * 0.55f)
                }
            }
            else -> {
                if (gridBoundsInRoot.width > 0f && gridBoundsInRoot.height > 0f) {
                    val cx = gridBoundsInRoot.center.x
                    val cy = gridBoundsInRoot.top + gridBoundsInRoot.height * 0.65f
                    val rw = gridBoundsInRoot.width * 0.42f
                    val rh = gridBoundsInRoot.height * 0.20f
                    Rect(cx - rw, cy - rh, cx + rw, cy + rh)
                } else {
                    Rect(wPx * 0.15f, hPx * 0.50f, wPx * 0.85f, hPx * 0.72f)
                }
            }
        }

        val animRect by animateRectAsState(
            targetValue = targetHighlight,
            animationSpec = spring(dampingRatio = 0.88f, stiffness = 420f),
            label = "spotlightRect",
        )
        val cornerPx = with(density) { if (step == 0) 34.dp.toPx() else 28.dp.toPx() }
        val strokePx = with(density) { 2.5.dp.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(Color.Black.copy(alpha = 0.62f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = animRect.topLeft,
                size = Size(animRect.width, animRect.height),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                blendMode = BlendMode.Clear,
            )
            drawRoundRect(
                color = ringColor,
                topLeft = animRect.topLeft,
                size = Size(animRect.width, animRect.height),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = strokePx),
            )
        }

        val (title, desc) = when (step) {
            0 -> stringResource(R.string.onboarding_step1_title) to stringResource(R.string.onboarding_step1_desc)
            1 -> stringResource(R.string.onboarding_step2_title) to stringResource(R.string.onboarding_step2_desc)
            else -> stringResource(R.string.onboarding_step3_title) to stringResource(R.string.onboarding_step3_desc)
        }

        val showAbove = targetHighlight.center.y > hPx * 0.55f
        val cardOffsetY = if (showAbove) {
            (targetHighlight.top - with(density) { 220.dp.toPx() }).coerceAtLeast(with(density) { 48.dp.toPx() })
        } else {
            (targetHighlight.bottom + with(density) { 18.dp.toPx() }).coerceAtMost(hPx - with(density) { 220.dp.toPx() })
        }

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, cardOffsetY.roundToInt()) }
                .padding(horizontal = 24.dp)
                .widthIn(min = 260.dp, max = 380.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "${step + 1}/3",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (step == 0) {
                    OutlinedButton(
                        onClick = onRestoreBackup,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.backup_restore_action))
                    }
                }

                if (step == 2 && !isDefaultHome && roleManager != null) {
                    FilledTonalButton(
                        onClick = {
                            roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_set_default_launcher))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onFinish) {
                        Text(stringResource(R.string.action_skip))
                    }
                    Spacer(Modifier.weight(1f))
                    if (step < 2) {
                        Button(onClick = { step++ }) {
                            Text(stringResource(R.string.action_next))
                        }
                    } else {
                        Button(onClick = onFinish) {
                            Text(stringResource(R.string.action_got_it))
                        }
                    }
                }
            }
        }
    }
}
