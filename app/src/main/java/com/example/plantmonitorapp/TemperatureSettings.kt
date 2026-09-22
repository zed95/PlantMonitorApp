package com.example.plantmonitorapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Temperature Settings",
            color = SettingsRed,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.fillMaxWidth(),
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

        Spacer(Modifier.height(40.dp))

        OutlinedButton(
            onClick = { Unit },
            shape = RoundedCornerShape(50),
            border = BorderStroke(2.dp, SettingsRed),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SettingsRed)
        ) {
            Text(text = "Update", fontWeight = FontWeight.Bold)
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
    Column(modifier = modifier) {
        Text(
            text = label,
            color = SettingsRed,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
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
                focusedBorderColor = SettingsRed,
                unfocusedBorderColor = SettingsRed,
                cursorColor = SettingsRed
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

class TemperatureSettingsViewModel(): ViewModel()
{
    private val _lowerActivationTh = MutableStateFlow("")
    val lowerActivationTh = _lowerActivationTh.asStateFlow()
    private val _lowerDeactivationTh = MutableStateFlow("")
    val lowerDeactivationTh = _lowerDeactivationTh.asStateFlow()
    private val _upperActivationTh = MutableStateFlow("")
    val upperActivationTh = _upperActivationTh.asStateFlow()
    private val _upperDectivationTh = MutableStateFlow("")
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
