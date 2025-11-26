package com.sakethh.otic

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakethh.otic.ui.theme.OticTheme

class OticActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val oticServiceIntent = Intent(this@OticActivity, OticService::class.java)
        enableEdgeToEdge()
        setContent {
            val oticVM = viewModel<OticVM>(factory = viewModelFactory {
                initializer {
                    OticVM(this@OticActivity)
                }
            })
            val runtimePermissionsLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsMap: Map<String, Boolean> ->
                    oticVM.updatePermissionsGrantedState(permissionsMap.all { it.value })
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
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.padding(start = 15.dp)) {
                                Text(
                                    text = "Otic",
                                    modifier = Modifier.alignByBaseline(),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(2.5.dp))
                                Text(text = "v1.0.0", modifier = Modifier.alignByBaseline())
                            }
                            AnimatedVisibility(!oticVM.allPermissionsGranted) {
                                Text(
                                    text = OticService.PERMISSION_REQUIRED_MESSAGE,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 15.dp, end = 15.dp),
                                )
                            }
                            AnimatedVisibility(OticService.isServiceRunning) {
                                Text(
                                    modifier = Modifier.padding(start = 15.dp, end = 15.dp),
                                    text = "Streaming on ${OticService.ipv4Address ?: "null"}:${OticService.serverPort}"
                                )
                            }
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
                                    Text(text = text)
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