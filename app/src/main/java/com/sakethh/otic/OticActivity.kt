package com.sakethh.otic

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakethh.otic.theme.OticTheme
import com.sakethh.otic.theme.googleSansFlexFontFamily

class OticActivity : ComponentActivity() {

    val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val oticServiceIntent = Intent(this@OticActivity, OticService::class.java)
        setContent {
            val oticVM = viewModel<OticVM>(factory = viewModelFactory {
                initializer {
                    OticVM(this@OticActivity)
                }
            })
            val localFocusManager = LocalFocusManager.current
            val runtimePermissionsLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsMap: Map<String, Boolean> ->
                    oticVM.updatePermissionsGrantedState(permissionsMap.all { it.value })
                }
            var serverPort by rememberSaveable(OticService.serverPort) {
                mutableIntStateOf(OticService.serverPort)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = {
                        finishAndRemoveTask()
                    }, interactionSource = null, indication = null),
                contentAlignment = Alignment.BottomCenter
            ) {
                OticTheme {
                    Surface {
                        Column(
                            modifier = Modifier
                                .clickable(enabled = false, onClick = {})
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                        ) {
                            HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = 5.dp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.padding(start = 15.dp)) {
                                Text(
                                    text = "Otic",
                                    modifier = Modifier.alignByBaseline(),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = googleSansFlexFontFamily
                                )
                                Text(
                                    text = "v1.0.0",
                                    modifier = Modifier.alignByBaseline(),
                                    fontFamily = googleSansFlexFontFamily
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            AnimatedVisibility(oticVM.allPermissionsGranted && !OticService.isServiceRunning) {
                                TextField(
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent,
                                        errorIndicatorColor = Color.Transparent
                                    ),
                                    textStyle = TextStyle(
                                        fontFamily = googleSansFlexFontFamily,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    shape = RoundedCornerShape(25.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 15.dp, end = 15.dp)
                                        .imePadding(),
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Port",
                                            fontFamily = googleSansFlexFontFamily,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    supportingText = {
                                        AnimatedVisibility(serverPort in 0..65535 && serverPort != OticService.serverPort) {
                                            Text(
                                                text = "Confirm port change using your keyboard",
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = googleSansFlexFontFamily,
                                                fontSize = 16.sp,
                                                lineHeight = 20.sp
                                            )
                                        }

                                        AnimatedVisibility(serverPort !in 0..65535) {
                                            Text(
                                                text = OticVM.VALID_PORT_MSG,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.error,
                                                fontFamily = googleSansFlexFontFamily,
                                                fontSize = 16.sp,
                                                lineHeight = 20.sp
                                            )
                                        }
                                    },
                                    isError = serverPort !in 0..65535,
                                    value = serverPort.toString(),
                                    onValueChange = {
                                        try {
                                            serverPort = it.toInt()
                                        } catch (_: Exception) {

                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Done,
                                        keyboardType = KeyboardType.Number
                                    ),
                                    keyboardActions = KeyboardActions(onDone = {
                                        oticVM.updatePersistedPortNumber(
                                            value = serverPort,
                                            onCompletion = {
                                                mainHandler.post {
                                                    localFocusManager.clearFocus(force = true)
                                                }
                                                OticService.updateServerPort(serverPort)
                                            },
                                            onError = {
                                                mainHandler.post {
                                                    Toast.makeText(
                                                        this@OticActivity, it, Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            })
                                    }),
                                    readOnly = OticService.isServiceRunning
                                )
                            }
                            AnimatedVisibility(!oticVM.allPermissionsGranted) {
                                Text(
                                    text = OticService.PERMISSION_REQUIRED_MESSAGE,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 15.dp, end = 15.dp),
                                    fontFamily = googleSansFlexFontFamily
                                )
                            }
                            AnimatedVisibility(OticService.isServiceRunning) {
                                Text(
                                    modifier = Modifier.padding(start = 15.dp, end = 15.dp),
                                    text = "Streaming on ${OticService.ipv4Address ?: "null"}:${OticService.serverPort}",
                                    fontFamily = googleSansFlexFontFamily
                                )
                            }
                            AnimatedVisibility(serverPort in 0..65535) {
                                Button(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 15.dp, end = 15.dp), onClick = {
                                        if (!oticVM.allPermissionsGranted) {
                                            runtimePermissionsLauncher.launch(OticService.permissions.toTypedArray())
                                            return@Button
                                        }

                                        if (OticService.isServiceRunning) {
                                            stopService(oticServiceIntent)
                                            return@Button
                                        }

                                        if (Build.VERSION.SDK_INT <= 25) {
                                            startService(oticServiceIntent)
                                        } else {
                                            startForegroundService(oticServiceIntent)
                                        }
                                    }) {
                                    AnimatedContent(targetState = if (!oticVM.allPermissionsGranted) "Grant" else if (!OticService.isServiceRunning) "Start Streaming" else "Stop Streaming") { text ->
                                        Text(
                                            text = text,
                                            fontFamily = googleSansFlexFontFamily,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun logger(string: String) {
    Log.d("OticService", string)
}