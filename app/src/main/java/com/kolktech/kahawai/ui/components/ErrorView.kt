package com.kolktech.kahawai.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/// Retry alone is a dead end for an expired/invalid session — retrying
/// the same request just fails the same way. When the failure is a 401
/// that survived TokenAuthenticator's own refresh attempt, offer a way
/// back to the login screen alongside the normal retry.
@Composable
fun ErrorView(
    message: String,
    isAuthError: Boolean,
    onRetry: () -> Unit,
    onSignInAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error)
            if (isAuthError) {
                Button(onClick = onSignInAgain, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Sign in again")
                }
                OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Retry")
                }
            } else {
                Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Retry")
                }
            }
        }
    }
}
