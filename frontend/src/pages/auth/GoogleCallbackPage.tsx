import React, { useEffect, useState, useRef } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Rocket, AlertCircle, ArrowLeft, Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/Button';
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
    <div className="min-h-screen bg-[#080B14] text-slate-100 flex flex-col justify-center py-12 sm:px-6 lg:px-8 font-sans antialiased relative overflow-hidden">
      {/* Ambient Lighting Orbs */}
      <div className="fixed inset-0 pointer-events-none overflow-hidden z-0 bg-grid-pattern opacity-30" />
      <div className="fixed inset-0 pointer-events-none overflow-hidden z-0">
        <div className="absolute -top-32 left-1/3 w-[500px] h-[500px] bg-purple-600/10 rounded-full blur-[140px]" />
        <div className="absolute -bottom-32 right-1/4 w-[500px] h-[500px] bg-blue-600/10 rounded-full blur-[140px]" />
      </div>

      <div className="sm:mx-auto sm:w-full sm:max-w-md relative z-10">
        {/* Brand Header */}
        <div className="flex items-center justify-center gap-3 mb-2">
          <div className="w-11 h-11 rounded-2xl bg-gradient-to-tr from-purple-700 via-purple-600 to-indigo-500 text-white flex items-center justify-center font-bold text-lg shadow-[0_0_24px_-3px_rgba(139,92,246,0.6)] border border-purple-400/30">
            <Rocket className="w-5 h-5 text-purple-100" />
          </div>
          <span className="font-bold tracking-tight text-2xl text-white font-mono">ShipIt</span>
        </div>
        <h2 className="text-center text-xs text-slate-400 font-mono tracking-wide">
          GOOGLE OAUTH IDENTITY EXCHANGE
        </h2>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md px-4 sm:px-0 relative z-10">
        <div className="bg-[#111629] border border-[#252D46] py-8 px-6 shadow-2xl rounded-2xl sm:px-10 space-y-6 text-center backdrop-blur-xl relative overflow-hidden">
          <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-[#8B5CF6] to-transparent" />
          
          {error ? (
            <div className="space-y-5">
              <div className="w-12 h-12 rounded-2xl bg-rose-500/10 border border-rose-500/25 text-rose-400 flex items-center justify-center mx-auto shadow-inner">
                <AlertCircle className="w-6 h-6" />
              </div>

              <div className="space-y-2">
                <h3 className="text-base font-bold text-white">
                  Authentication Failed
                </h3>
                <p className="text-xs text-slate-400 leading-relaxed font-mono">
                  {error}
                </p>
              </div>

              <div className="pt-2">
                <Link to="/login">
                  <Button
                    variant="secondary"
                    size="md"
                    fullWidth={true}
                    leftIcon={<ArrowLeft className="w-4 h-4" />}
                  >
                    Return to Sign In
                  </Button>
                </Link>
              </div>
            </div>
          ) : (
            <div className="space-y-5 py-4">
              {/* Google Animated Icon */}
              <div className="relative w-16 h-16 mx-auto flex items-center justify-center">
                <div className="absolute inset-0 rounded-full bg-blue-500/20 animate-ping opacity-60" />
                <div className="relative w-14 h-14 rounded-2xl bg-[#080B14] border border-[#252D46] flex items-center justify-center shadow-lg">
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
                <h3 className="text-sm font-semibold text-white flex items-center justify-center gap-2">
                  <Loader2 className="w-4 h-4 animate-spin text-[#8B5CF6]" />
                  <span>Signing In</span>
                </h3>
                <p className="text-xs text-slate-400 font-mono">
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
