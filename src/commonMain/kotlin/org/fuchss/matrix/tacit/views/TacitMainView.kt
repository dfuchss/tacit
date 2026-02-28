package org.fuchss.matrix.tacit.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.root.MainView
import de.connect2x.trixnity.messenger.compose.view.root.MainViewImpl
import de.connect2x.trixnity.messenger.viewmodel.MainViewModel

private val footerHeight = 56.dp

class TacitMainView(
    private val delegate: MainView = MainViewImpl(),
) : MainView {
    @Composable
    override fun create(mainViewModel: MainViewModel) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = footerHeight)
            ) {
                delegate.create(mainViewModel)
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(footerHeight)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                TacitAttributionFooter(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    textStyle = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
