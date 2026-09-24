package com.example.plantmonitorapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plantmonitorapp.ui.theme.BackgroundGrey
import com.example.plantmonitorapp.ui.theme.CustomSilver
import com.example.plantmonitorapp.ui.theme.ElevatedGrey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private val SettingsRed = Color(0xFFE53935)

// Matches "", "-", "12", "12.", "12.5", "-.5" etc. so valid partial input
// while typing is still accepted, but stray characters and a second "." or
// "-" are rejected.
private val floatingPointRegex = Regex("^-?\\d*\\.?\\d*$")

private fun isValidFloatingPointInput(input: String): Boolean =
    input.matches(floatingPointRegex)

/**
 * Stateless by design — current values and change callbacks are hoisted so a
 * ViewModel can own the state (e.g. expose a TemperatureSettingsUiState via
 * StateFlow and call onUpdateClick to push the new values over Bluetooth).
 */
@Composable
fun TemperatureSettingsScreen(tempSettingsViewModel: TemperatureSettingsViewModel = viewModel()
) {
    val lowerActivationTh by tempSettingsViewModel.lowerActivationTh.collectAsStateWithLifecycle()
    val lowerDeactivationTh by tempSettingsViewModel.lowerDeactivationTh.collectAsStateWithLifecycle()
    val upperActivationTh by tempSettingsViewModel.upperActivationTh.collectAsStateWithLifecycle()
    val upperDeactivationTh by tempSettingsViewModel.upperDectivationTh.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGrey)
    )
    {
        ElevatedCard(
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(10.dp),
            modifier = Modifier
                .padding(top = 10.dp)
                .weight(1.0f)
        )
        {
            Box(
                modifier = Modifier
                    .fillMaxWidth(1.0f)
                    .background(ElevatedGrey),   // optional spacing from the top

            )
            {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ElevatedGrey),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Temperature Settings",
                        color = CustomSilver,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 20.dp, top = 10.dp)
                    )

                    SectionDivider("Thresholds", CustomSilver, )

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth()
                                           .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        TemperatureField(
                            label = "Lower Activation",
                            value = lowerActivationTh,
                            onValueChange = tempSettingsViewModel::updateLowerActivationTh,
                            modifier = Modifier.weight(1f)
                        )
                        TemperatureField(
                            label = "Upper Activation",
                            value = upperActivationTh,
                            onValueChange = tempSettingsViewModel::updateUpperActivationTh,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth()
                                           .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        TemperatureField(
                            label = "Lower Deactivation",
                            value = lowerDeactivationTh,
                            onValueChange = tempSettingsViewModel::updateLowerDeactivationTh,
                            modifier = Modifier.weight(1f)
                        )
                        TemperatureField(
                            label = "Upper Deactivation",
                            value = upperDeactivationTh,
                            onValueChange = tempSettingsViewModel::updateUpperDeactivationTh,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ExtendedFloatingActionButton(
                            elevation = FloatingActionButtonDefaults.elevation(10.dp),
                            containerColor = CustomSilver,
                            text = { Text(text = "Update",
                                          color = Color.Black) },
                            icon = { Icon(Icons.Filled.ArrowCircleRight,
                                          "Extended floating action button.",
                                            tint = Color.Black) },
                            onClick = { Unit },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemperatureField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                if (isValidFloatingPointInput(newValue)) {
                    onValueChange(newValue)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CustomSilver,
                unfocusedBorderColor = CustomSilver,
                cursorColor = CustomSilver
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Fixed-size label, always pinned to the border. The background
        // "erases" the border line behind the text to fake the notch —
        // swap it for whatever this field actually sits on.
        Text(
            text = label,
            color = CustomSilver,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 12.dp, y = (-12).dp)
                .background(ElevatedGrey)
                .padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun SectionDivider(
    title: String,
    color: Color = MaterialTheme.colorScheme.outline,
    thickness: Dp = 1.dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
                           .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = thickness,
            color = color
        )
        Text(
            text = title,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = thickness,
            color = color
        )
    }
}

class TemperatureSettingsViewModel(): ViewModel()
{
    private val _lowerActivationTh = MutableStateFlow("0.0")
    val lowerActivationTh = _lowerActivationTh.asStateFlow()
    private val _lowerDeactivationTh = MutableStateFlow("0.0")
    val lowerDeactivationTh = _lowerDeactivationTh.asStateFlow()
    private val _upperActivationTh = MutableStateFlow("0.0")
    val upperActivationTh = _upperActivationTh.asStateFlow()
    private val _upperDectivationTh = MutableStateFlow("0.0")
    val upperDectivationTh = _upperDectivationTh.asStateFlow()

    fun updateLowerActivationTh(strFloat: String)
    {
        _lowerActivationTh.value = strFloat
    }

    fun updateUpperActivationTh(strFloat: String)
    {
        _upperActivationTh.value = strFloat
    }

    fun updateLowerDeactivationTh(strFloat: String)
    {
        _lowerDeactivationTh.value = strFloat
    }

    fun updateUpperDeactivationTh(strFloat: String)
    {
        _upperDectivationTh.value = strFloat
    }
}
