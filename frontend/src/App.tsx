import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ProjectProvider } from './context/ProjectContext';
import { ToastProvider } from './context/ToastContext';
import { ProtectedRoute } from './components/auth/ProtectedRoute';
import { MainLayout } from './components/layout/MainLayout';
import { LoginPage } from './pages/auth/LoginPage';
import { RegisterPage } from './pages/auth/RegisterPage';
import { GitHubCallbackPage } from './pages/auth/GitHubCallbackPage';
import { GoogleCallbackPage } from './pages/auth/GoogleCallbackPage';
import { OverviewPage } from './pages/OverviewPage';
import { DeploymentsPage } from './pages/DeploymentsPage';
import { ContainersPage } from './pages/ContainersPage';
import { EnvVarsPage } from './pages/EnvVarsPage';
import { SettingsPage } from './pages/SettingsPage';

function App() {
  return (
    <AuthProvider>
      <ProjectProvider>
        <ToastProvider>
          <BrowserRouter>
            <Routes>
              {/* Public Authentication Routes */}
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/auth/callback/github" element={<GitHubCallbackPage />} />
              <Route path="/auth/callback/google" element={<GoogleCallbackPage />} />

              {/* Protected Application Workspace */}
              <Route
                path="/"
                element={
                  <ProtectedRoute>
                    <MainLayout />
                  </ProtectedRoute>
                }
              >
                <Route index element={<OverviewPage />} />
                <Route path="deployments" element={<DeploymentsPage />} />
                <Route path="containers" element={<ContainersPage />} />
                <Route path="env-vars" element={<EnvVarsPage />} />
                <Route path="settings" element={<SettingsPage />} />
              </Route>

              {/* Catch-all redirect */}
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </ToastProvider>
      </ProjectProvider>
    </AuthProvider>
  );
}

export default App;
