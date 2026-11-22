import React, { useEffect, useState, useRef } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Rocket, AlertCircle, ArrowLeft, Loader2 } from 'lucide-react';
import axios from 'axios';

export const GoogleCallbackPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { loginWithGoogle } = useAuth();

  const [error, setError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState('Connecting with Google...');
  const hasExchanged = useRef(false);

  useEffect(() => {
    // Prevent double execution in React StrictMode
    if (hasExchanged.current) return;

    const code = searchParams.get('code');
    const googleError = searchParams.get('error');

    if (googleError) {
      hasExchanged.current = true;
      setError(`Google authorization was cancelled or failed: ${googleError}`);
      return;
    }

    if (!code) {
      hasExchanged.current = true;
      setError('No authorization code was received from Google.');
      return;
    }

    hasExchanged.current = true;

    const exchangeCode = async () => {
      try {
        setStatusMessage('Exchanging authorization code and verifying identity...');
        const redirectUri = window.location.origin + '/auth/callback/google';

        await loginWithGoogle({
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
              'An account with this email already exists. Please log in with your email and password to link your Google account.'
            );
          } else {
            setError(apiError || 'Failed to authenticate with Google. Please try again.');
          }
        } else {
          setError('An unexpected error occurred during Google login.');
        }
      }
    };

    exchangeCode();
  }, [searchParams, loginWithGoogle, navigate]);

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
          Google OAuth Authentication
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
              {/* Google Animated Icon */}
              <div className="relative w-16 h-16 mx-auto flex items-center justify-center">
                <div className="absolute inset-0 rounded-full bg-blue-500/20 animate-ping opacity-75" />
                <div className="relative w-14 h-14 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center shadow-lg">
                  <svg className="w-7 h-7" viewBox="0 0 24 24">
                    <path
                      fill="#4285F4"
                      d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.82-2.4 3.68v3.05h3.88c2.27-2.09 3.66-5.17 3.66-9.17z"
                    />
                    <path
                      fill="#34A853"
                      d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.1-6.72-4.93H1.25v3.15C3.26 21.36 7.33 24 12 24z"
                    />
                    <path
                      fill="#FBBC05"
                      d="M5.28 14.27c-.25-.72-.38-1.49-.38-2.27s.13-1.55.38-2.27V6.58H1.25C.45 8.18 0 10.04 0 12s.45 3.82 1.25 5.42l4.03-3.15z"
                    />
                    <path
                      fill="#EA4335"
                      d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.33 0 3.26 2.64 1.25 6.58l4.03 3.15c.95-2.83 3.6-4.98 6.72-4.98z"
                    />
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
