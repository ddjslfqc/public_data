/**
 * LoginScreen Compose 预览参考文件
 *
 * 本文件展示登录页的 Jetpack Compose 实现，作为后续开发的参考。
 * 真实实现应放在 app 或 login 模块下，并接入实际的 ViewModel。
 *
 * 设计参考：docs/ui_design.md
 */

@file:Suppress("unused")

package com.fuusy.login.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// 颜色规范（对应 ui_design.md 2. 颜色规范）
// ============================================================================

private object LoginColors {
    val Background    = Color(0xFF0F1923)
    val Surface       = Color(0xFF1A2736)
    val SurfaceVariant= Color(0xFF243447)
    val Primary       = Color(0xFF1A3A5C)
    val PrimaryDark   = Color(0xFF0D2240)
    val Accent        = Color(0xFF00B4D8)
    val TextPrimary   = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA8B8C8)
    val TextHint      = Color(0xFF5A7080)
    val Divider       = Color(0xFF2D4056)
    val Error         = Color(0xFFFF3D00)
}

// ============================================================================
// 入口函数
// ============================================================================

@Composable
fun LoginScreen(
    state: LoginState = LoginState(),
    onUsernameChange: (String) -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onLoginClick: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
    onLogoTripleClick: () -> Unit = {},
    onServerConfigConfirm: (String) -> Unit = {},
) {
    var passwordVisible by remember { mutableStateOf(false) }

    // Logo 三击隐藏入口
    var clickCount by remember { mutableIntStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }

    fun handleLogoClick() {
        val now = System.currentTimeMillis()
        if (now - lastClickTime < 400) {
            clickCount++
            if (clickCount >= 3) {
                clickCount = 0
                onLogoTripleClick()
            }
        } else {
            clickCount = 1
        }
        lastClickTime = now
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LoginColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(100.dp))

            // Logo 图标
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(LoginColors.Primary)
                    .clickable { handleLogoClick() },
                contentAlignment = Alignment.Center,
            ) {
                // TODO: 替换为实际 Logo 资源
                // Image(painterResource("drawable/ic_logo.xml"), ...)
                Text(
                    text = "巡",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = LoginColors.Accent,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 主标题
            Text(
                text = "巡检监测",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = LoginColors.TextPrimary,
            )

            // 副标题
            Text(
                text = "安全管理平台",
                fontSize = 14.sp,
                color = LoginColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 用户名输入框
            LoginTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = "用户名",
                leadingIcon = Icons.Default.Person,
                imeAction = ImeAction.Next,
                isError = state.usernameError != null,
                errorMessage = state.usernameError,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 密码输入框
            LoginTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = "密码",
                leadingIcon = Icons.Default.Lock,
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff
                                          else Icons.Default.Visibility,
                            contentDescription = "切换密码可见性",
                            tint = LoginColors.TextHint,
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onLoginClick() }
                ),
                isError = state.passwordError != null,
                errorMessage = state.passwordError,
                modifier = Modifier.fillMaxWidth(),
            )

            // 错误提示
            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.errorMessage,
                    fontSize = 12.sp,
                    color = LoginColors.Error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 登录按钮
            Button(
                onClick = onLoginClick,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = LoginColors.Primary,
                    contentColor = LoginColors.TextPrimary,
                    disabledBackgroundColor = LoginColors.Primary.copy(alpha = 0.4f),
                ),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = LoginColors.TextPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("登录中...", fontSize = 16.sp)
                } else {
                    Text(
                        text = "登 录",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 注册入口
            Text(
                text = "没有账号？立即注册",
                fontSize = 14.sp,
                color = LoginColors.Accent,
                modifier = Modifier.clickable { onRegisterClick() }
            )

            Spacer(modifier = Modifier.weight(1f))

            // 版本号
            Text(
                text = "版本号 v1.0.0",
                fontSize = 12.sp,
                color = LoginColors.TextHint,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        // 服务器配置 BottomSheet
        if (state.showServerConfig) {
            ServerConfigBottomSheet(
                currentIp = state.serverIp,
                onDismiss = { /* 关闭 */ },
                onConfirm = onServerConfigConfirm,
            )
        }
    }
}

// ============================================================================
// 子组件
// ============================================================================

@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
) {
    val borderColor by animateFloatAsState(
        targetValue = if (isError) 1f else 0f,
        label = "borderColor",
    )

    val actualBorderColor = if (isError) LoginColors.Error else Color.Transparent

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, color = LoginColors.TextHint) },
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isError) LoginColors.Error else LoginColors.TextSecondary,
                )
            },
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .border(
                    width = 1.dp,
                    color = actualBorderColor,
                    shape = RoundedCornerShape(12.dp),
                ),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = LoginColors.Surface,
                textColor = LoginColors.TextPrimary,
                cursorColor = LoginColors.Accent,
                focusedBorderColor = LoginColors.Accent,
                unfocusedBorderColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(12.dp),
        )
    }
}

@Composable
private fun ServerConfigBottomSheet(
    currentIp: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var ipInput by remember { mutableStateOf(currentIp) }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(LoginColors.Surface)
                .clickable { /* 阻止冒泡 */ }
                .padding(24.dp),
        ) {
            Text(
                text = "服务器配置",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = LoginColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it },
                label = { Text("服务器 IP", color = LoginColors.TextHint) },
                placeholder = { Text("例：8.130.120.35:9220", color = LoginColors.TextHint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onConfirm(ipInput)
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = LoginColors.SurfaceVariant,
                    textColor = LoginColors.TextPrimary,
                    cursorColor = LoginColors.Accent,
                    focusedBorderColor = LoginColors.Accent,
                    unfocusedBorderColor = LoginColors.Divider,
                ),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = LoginColors.TextSecondary,
                    ),
                ) {
                    Text("取消")
                }

                Button(
                    onClick = { onConfirm(ipInput) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = LoginColors.Primary,
                        contentColor = LoginColors.TextPrimary,
                    ),
                ) {
                    Text("确认")
                }
            }
        }
    }
}

// ============================================================================
// 状态类
// ============================================================================

data class LoginState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val showServerConfig: Boolean = false,
    val serverIp: String = "8.130.120.35:9220",
)

// ============================================================================
// 预览
// ============================================================================

@Preview(name = "LoginScreen - 默认", showBackground = true)
@Composable
private fun LoginScreenPreview() {
    LoginScreen(
        state = LoginState(
            username = "",
            password = "",
            serverIp = "8.130.120.35:9220",
        ),
    )
}

@Preview(name = "LoginScreen - 输入中", showBackground = true)
@Composable
private fun LoginScreenInputPreview() {
    LoginScreen(
        state = LoginState(
            username = "admin",
            password = "123456",
        ),
    )
}

@Preview(name = "LoginScreen - 登录中", showBackground = true)
@Composable
private fun LoginScreenLoadingPreview() {
    LoginScreen(
        state = LoginState(
            username = "admin",
            password = "123456",
            isLoading = true,
        ),
    )
}

@Preview(name = "LoginScreen - 错误", showBackground = true)
@Composable
private fun LoginScreenErrorPreview() {
    LoginScreen(
        state = LoginState(
            username = "admin",
            password = "wrong",
            errorMessage = "用户名或密码错误",
            passwordError = "密码不正确",
        ),
    )
}

@Preview(name = "LoginScreen - 服务器配置", showBackground = true)
@Composable
private fun LoginScreenServerConfigPreview() {
    LoginScreen(
        state = LoginState(
            showServerConfig = true,
            serverIp = "8.130.120.35:9220",
        ),
    )
}