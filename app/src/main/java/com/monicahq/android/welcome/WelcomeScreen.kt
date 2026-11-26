package com.monicahq.android.welcome

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monicahq.android.R
import com.monicahq.android.instance.instanceHost
import com.monicahq.android.ui.theme.MonicaTheme
import com.monicahq.android.ui.theme.WordmarkStyle

private const val ILLUSTRATION_ASPECT_RATIO = 880f / 530f

/** Connects [WelcomeScreen] to its [WelcomeViewModel]. Signing up and logging in come later. */
@Composable
fun WelcomeRoute(viewModel: WelcomeViewModel = viewModel(factory = WelcomeViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WelcomeScreen(
        uiState = uiState,
        onSignUp = {},
        onLogIn = {},
        onOpenInstanceSheet = viewModel::openInstanceSheet,
        onDraftChange = viewModel::onDraftChange,
        onResetDraft = viewModel::resetDraft,
        onDismissInstanceSheet = viewModel::dismissInstanceSheet,
        onSaveInstance = viewModel::saveInstance,
    )
}

/**
 * The first screen of the app: who Monica is, and the ways in (sign up, log in, choose the
 * instance). Stateless: it shows [uiState] and reports taps through the callbacks.
 */
@Composable
fun WelcomeScreen(
    uiState: WelcomeUiState,
    onSignUp: () -> Unit,
    onLogIn: () -> Unit,
    onOpenInstanceSheet: () -> Unit,
    onDraftChange: (String) -> Unit,
    onResetDraft: () -> Unit,
    onDismissInstanceSheet: () -> Unit,
    onSaveInstance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Surface provides the page colour and, as content colour, the ink colour that icons,
    // ripples and focus highlights use.
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        WelcomeContent(
            instanceHost = instanceHost(uiState.instanceUrl),
            onSignUp = onSignUp,
            onLogIn = onLogIn,
            onInstanceSettings = onOpenInstanceSheet,
        )
    }
    uiState.sheet?.let {
        InstanceSheet(
            state = it,
            onDraftChange = onDraftChange,
            onReset = onResetDraft,
            onDismiss = onDismissInstanceSheet,
            onSave = onSaveInstance,
        )
    }
}

@Composable
private fun WelcomeContent(
    instanceHost: String,
    onSignUp: () -> Unit,
    onLogIn: () -> Unit,
    onInstanceSettings: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        // At least as tall as the screen, so the buttons sit at the bottom of tall screens, and
        // scrollable, so nothing is cut on small screens or with a large font.
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TopBar(onInstanceSettings = onInstanceSettings)
                Brand()
                Illustration(modifier = Modifier.padding(top = 16.dp))
                Introduction(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp))
            }
            Column(modifier = Modifier.padding(top = 24.dp)) {
                Actions(
                    onSignUp = onSignUp,
                    onLogIn = onLogIn,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                InstanceLine(
                    host = instanceHost,
                    onClick = onInstanceSettings,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun TopBar(onInstanceSettings: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onInstanceSettings, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.welcome_instance_settings),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun Brand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Decorative: the name is written right next to it.
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            modifier = Modifier.size(width = 40.dp, height = 36.dp),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = WordmarkStyle,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun Illustration(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.panda_welcome),
        contentDescription = stringResource(R.string.welcome_illustration),
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(ILLUSTRATION_ASPECT_RATIO)
                // Fades the edges into the page, as the design's elliptical mask does
                // (radii 72% × 70%, fully opaque up to 62% of the radius).
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                // Black and transparent are only an alpha mask here: no colour reaches the screen.
                .drawWithContent {
                    drawContent()
                    val radiusX = size.width * 0.72f
                    val radiusY = size.height * 0.70f
                    val stretch = radiusX / radiusY
                    scale(scaleX = 1f, scaleY = 1f / stretch, pivot = center) {
                        drawRect(
                            brush =
                                Brush.radialGradient(
                                    0.62f to Color.Black,
                                    1f to Color.Transparent,
                                    center = center,
                                    radius = radiusX,
                                ),
                            topLeft = Offset(0f, center.y - size.height * stretch / 2),
                            size = Size(size.width, size.height * stretch),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
    )
}

@Composable
private fun Introduction(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.welcome_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Actions(
    onSignUp: () -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // heightIn rather than height: with a large font the label wraps instead of being cut.
        Button(
            onClick = onSignUp,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(text = stringResource(R.string.welcome_sign_up), textAlign = TextAlign.Center)
        }
        OutlinedButton(
            onClick = onLogIn,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
            // The design's "hairline-strong" border (Material's default is outlineVariant).
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Text(text = stringResource(R.string.welcome_log_in), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun InstanceLine(
    host: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = stringResource(R.string.welcome_instance, host)
    val hostStyle = SpanStyle(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.welcome_instance_settings),
                    onClick = onClick,
                ).heightIn(min = 48.dp)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_instance),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = highlight(text, host, hostStyle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "Light", showSystemUi = true)
@Preview(name = "Dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WelcomeScreenPreview() {
    MonicaTheme {
        WelcomeScreen(
            uiState = WelcomeUiState(),
            onSignUp = {},
            onLogIn = {},
            onOpenInstanceSheet = {},
            onDraftChange = {},
            onResetDraft = {},
            onDismissInstanceSheet = {},
            onSaveInstance = {},
        )
    }
}
