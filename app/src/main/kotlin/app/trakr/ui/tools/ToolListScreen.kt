package app.trakr.ui.tools

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ToolListScreen(modifier: Modifier = Modifier) {
    Text(
        text = "Lista completa de ferramentas (em breve)",
        modifier = modifier.padding(16.dp),
    )
}