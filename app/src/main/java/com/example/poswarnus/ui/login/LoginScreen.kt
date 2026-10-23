package com.example.poswarnus.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.poswarnus.ui.components.BrandMark
import com.example.poswarnus.ui.components.EyeGlyph
import com.example.poswarnus.ui.components.LightSystemBarIcons
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import com.example.poswarnus.ui.theme.AccentOrange
import com.example.poswarnus.ui.theme.BackgroundCream
import com.example.poswarnus.ui.theme.ErrorRed
import com.example.poswarnus.ui.theme.ErrorTint
import com.example.poswarnus.ui.theme.LineStrong
import com.example.poswarnus.ui.theme.SurfaceWhite
import com.example.poswarnus.ui.theme.TextPrimary
import com.example.poswarnus.ui.theme.TextSecondary
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.TextUnit

// Two-pane layout — docs/design-reference.md "Login" § left pane is a fixed 480dp accent panel,
// the form column on the right is capped at 400dp regardless of window width. Below
// WideLayoutMinWidth (portrait phones) the brand block stacks above the form instead.
private val BrandPaneWidth = 480.dp
private val FormColumnWidth = 400.dp
private val WideLayoutMinWidth = 900.dp

@Composable
fun LoginScreen(
    onNavigateToDashboard: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is LoginEvent.NavigateToDashboard) onNavigateToDashboard()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideLayoutMinWidth
        if (!wide) LightSystemBarIcons(lightStatusBar = false)
        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(BrandPaneWidth)
                        .fillMaxHeight()
                        .background(AccentOrange)
                        .padding(48.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    BrandRow()
                    Column {
                        BrandTitle(fontSize = 36.sp, lineHeight = 40.sp)
                        BrandTagline()
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(BackgroundCream)
                        .imePadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoginForm(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.width(FormColumnWidth).verticalScroll(rememberScrollState()),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundCream)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentOrange)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    BrandRow()
                    Column {
                        BrandTitle(fontSize = 28.sp, lineHeight = 32.sp)
                        BrandTagline()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    LoginForm(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.widthIn(max = FormColumnWidth).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BrandMark(background = SurfaceWhite, foreground = AccentOrange, modifier = Modifier.size(40.dp))
        Text(
            text = "RestoPOS",
            style = MaterialTheme.typography.titleMedium,
            color = SurfaceWhite,
        )
    }
}

@Composable
private fun BrandTitle(fontSize: TextUnit, lineHeight: TextUnit) {
    Text(
        text = "Warung Nusantara",
        style = MaterialTheme.typography.headlineLarge.copy(fontSize = fontSize, lineHeight = lineHeight),
        color = SurfaceWhite,
    )
}

@Composable
private fun BrandTagline() {
    Text(
        text = "Masuk untuk memulai shift kasir.",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
        color = SurfaceWhite.copy(alpha = 0.92f),
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun LoginForm(
    state: LoginUiState,
    viewModel: LoginViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = "Masuk", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Gunakan akun kasir yang diberikan manajer.",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )

        LoginField(
            label = "Email",
            value = state.email,
            onValueChange = viewModel::onEmailChanged,
            placeholder = "nama@restoran.id",
            enabled = !state.isLoading,
            keyboardType = KeyboardType.Email,
            modifier = Modifier.padding(top = 28.dp),
        )

        var showPassword by remember { mutableStateOf(false) }
        LoginField(
            label = "Kata sandi",
            value = state.password,
            onValueChange = viewModel::onPasswordChanged,
            placeholder = "Masukkan kata sandi",
            enabled = !state.isLoading,
            keyboardType = KeyboardType.Password,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .semantics {
                        contentDescription = if (showPassword) "Sembunyikan kata sandi" else "Tampilkan kata sandi"
                    }
                    .clickable(role = Role.Button) { showPassword = !showPassword },
                    contentAlignment = Alignment.Center,
                ) {
                    EyeGlyph(visible = showPassword, tint = TextSecondary, modifier = Modifier.size(22.dp))
                }
            },
            modifier = Modifier.padding(top = 20.dp),
        )

        state.errorMessage?.let { message ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ErrorTint,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        Button(
            onClick = viewModel::onLoginClicked,
            enabled = !state.isLoading,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentOrange,
                contentColor = SurfaceWhite,
                disabledContainerColor = AccentOrange.copy(alpha = 0.6f),
                disabledContentColor = SurfaceWhite,
            ),
            modifier = Modifier.padding(top = 24.dp).fillMaxWidth().height(56.dp),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    color = SurfaceWhite,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp).padding(end = 8.dp),
                )
            }
            Text(
                text = if (state.isLoading) "Memverifikasi…" else "Masuk",
                style = MaterialTheme.typography.titleSmall,
            )
        }

        Text(
            text = "Lupa kata sandi? Hubungi manajer.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
}

/**
 * Field style per docs/design-reference.md "Login": 52dp height, 1px line-strong border, 6dp
 * radius, 16dp horizontal padding, 16sp text; focus = accent border.
 */
@Composable
private fun LoginField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) AccentOrange else LineStrong

    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(52.dp)
                .background(if (enabled) SurfaceWhite else BackgroundCream, RoundedCornerShape(6.dp))
                .border(1.dp, borderColor, RoundedCornerShape(6.dp)),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    color = if (enabled) TextPrimary else TextSecondary,
                ),
                cursorBrush = SolidColor(AccentOrange),
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp, end = if (trailing != null) 48.dp else 16.dp)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { innerField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
                                color = TextSecondary,
                            )
                        }
                        innerField()
                    }
                },
            )
            if (trailing != null) {
                Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) { trailing() }
            }
        }
    }
}
