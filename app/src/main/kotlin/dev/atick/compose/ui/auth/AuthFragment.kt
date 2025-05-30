package dev.atick.compose.ui.auth

import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.atick.compose.ui.theme.ComposeTheme
import dev.atick.core.ui.BaseComposeFragment
import dev.atick.core.utils.extensions.observeEvent
import dev.atick.core.utils.extensions.showToast

@AndroidEntryPoint
class AuthFragment : BaseComposeFragment() {

    private val viewModel: AuthViewModel by viewModels()

    @Composable
    override fun ComposeUi() {
        ComposeTheme {
            AuthScreen(viewModel)
        }
    }

    override fun observeStates() {
        super.observeStates()

        // Navigate to dashboard on successful authentication
        observeEvent(viewModel.authSuccess) { success ->
            if (success) {
                findNavController().navigate(
                    AuthFragmentDirections.actionAuthFragmentToDashboardFragment()
                )
            }
        }

        // Show error messages
        observeEvent(viewModel.authError) { errorMessage ->
            requireContext().showToast(errorMessage)
        }
    }
}
