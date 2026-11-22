import React, { useEffect, useState, useRef } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Rocket, AlertCircle, ArrowLeft, Loader2 } from 'lucide-react';
import axios from 'axios';

export const GitHubCallbackPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { loginWithGitHub } = useAuth();

  const [error, setError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState('Connecting with GitHub...');
  const hasExchanged = useRef(false);

  useEffect(() => {
    // Prevent double execution in React StrictMode
    if (hasExchanged.current) return;

    const code = searchParams.get('code');
    const githubError = searchParams.get('error');
    const errorDescription = searchParams.get('error_description');

    if (githubError) {
      hasExchanged.current = true;
      setError(errorDescription || `GitHub authorization failed: ${githubError}`);
      return;
    }

    if (!code) {
      hasExchanged.current = true;
      setError('No authorization code was received from GitHub.');
      return;
    }

    hasExchanged.current = true;

    const exchangeCode = async () => {
      try {
        setStatusMessage('Exchanging authorization code and verifying identity...');
        const redirectUri = window.location.origin + '/auth/callback/github';
        
        await loginWithGitHub({
          code,
          redirectUri,
        });

        setStatusMessage('Authentication successful! Redirecting to workspace...');
        // Smooth transition
        setTimeout(() => {
          navigate('/', { replace: true });
        }, 500);
      } catch (err: unknown) {
        if (axios.isAxiosError(err)) {
          const apiError = err.response?.data?.error || err.response?.data?.message;
          const errorCode = err.response?.data?.errorCode;

          if (errorCode === 'ACCOUNT_EXISTS_LINK_REQUIRED') {
            setError(
              'An account with this email already exists. Please log in with your email and password to link your GitHub account.'
            );
          } else {
            setError(apiError || 'Failed to authenticate with GitHub. Please try again.');
          }
        } else {
          setError('An unexpected error occurred during GitHub login.');
        }
      }
    };

    exchangeCode();
  }, [searchParams, loginWithGitHub, navigate]);

  return (
    <div className="min-h-screen bg-[#0b0f19] flex flex-col justify-center py-12 sm:px-6 lg:px-8 font-sans antialiased">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        {/* Brand Header */}
        <div className="flex items-center justify-center gap-2.5 mb-2">
          <div className="w-10 h-10 rounded-xl bg-blue-600 text-white flex items-center justify-center font-bold text-lg shadow-lg shadow-blue-500/20">
            <Rocket className="w-5 h-5" />
          </div>
          <span className="font-bold tracking-tight text-2xl text-slate-100">ShipIt</span>
        </div>
        <h2 className="text-center text-sm text-slate-400">
          GitHub OAuth Authentication
        </h2>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md px-4 sm:px-0">
        <div className="bg-slate-900 border border-slate-800 py-8 px-6 shadow-xl rounded-2xl sm:px-10 space-y-6 text-center">
          {error ? (
            <div className="space-y-5">
              <div className="w-12 h-12 rounded-full bg-rose-500/10 border border-rose-500/20 text-rose-400 flex items-center justify-center mx-auto">
                <AlertCircle className="w-6 h-6" />
              </div>

              <div className="space-y-2">
                <h3 className="text-base font-semibold text-slate-200">
                  Authentication Failed
                </h3>
                <p className="text-xs text-slate-400 leading-relaxed">
                  {error}
                </p>
              </div>

              <div className="pt-2">
                <Link
                  to="/login"
                  className="inline-flex items-center justify-center gap-2 w-full py-2.5 px-4 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold border border-slate-700 transition-colors"
                >
                  <ArrowLeft className="w-4 h-4" />
                  <span>Return to Sign In</span>
                </Link>
              </div>
            </div>
          ) : (
            <div className="space-y-5 py-4">
              {/* GitHub Animated Icon */}
              <div className="relative w-16 h-16 mx-auto flex items-center justify-center">
                <div className="absolute inset-0 rounded-full bg-blue-500/20 animate-ping opacity-75" />
                <div className="relative w-14 h-14 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center shadow-lg">
                  <svg className="w-7 h-7 text-white fill-current" viewBox="0 0 24 24">
                    <path fillRule="evenodd" clipRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" />
                  </svg>
                </div>
              </div>

              <div className="space-y-1.5">
                <h3 className="text-sm font-semibold text-slate-200 flex items-center justify-center gap-2">
                  <Loader2 className="w-4 h-4 animate-spin text-blue-500" />
                  <span>Signing In</span>
                </h3>
                <p className="text-xs text-slate-400">
                  {statusMessage}
                </p>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
