import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Rocket, Lock, Mail, AlertCircle, ArrowRight } from 'lucide-react';
import { GitHubOAuthButton } from '../../components/auth/GitHubOAuthButton';
import { GoogleOAuthButton } from '../../components/auth/GoogleOAuthButton';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import axios from 'axios';

export const LoginPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      await login({ email, password });
      navigate(from, { replace: true });
    } catch (err: unknown) {
      if (axios.isAxiosError(err)) {
        const msg = err.response?.data?.message || err.response?.data?.error || 'Invalid credentials. Please try again.';
        setError(msg);
      } else {
        setError('An unexpected error occurred. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

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
          DEVELOPER DEPLOYMENT PLATFORM
        </h2>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md px-4 sm:px-0 relative z-10">
        <div className="bg-[#111629] border border-[#252D46] py-8 px-6 shadow-2xl rounded-2xl sm:px-10 space-y-6 relative overflow-hidden backdrop-blur-xl">
          <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-[#8B5CF6] to-transparent" />
          
          {error && (
            <div className="p-3.5 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {/* OAuth Providers */}
          <div className="space-y-4">
            <div className="space-y-2.5">
              <GitHubOAuthButton onError={(err) => setError(err)} />
              <GoogleOAuthButton onError={(err) => setError(err)} />
            </div>

            <div className="relative flex items-center justify-center">
              <div className="border-t border-[#252D46] w-full" />
              <span className="bg-[#111629] px-3 text-[10px] font-medium uppercase tracking-wider text-slate-500 font-mono shrink-0">
                Or continue with email
              </span>
              <div className="border-t border-[#252D46] w-full" />
            </div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4 text-xs">
            <div>
              <label className="block text-slate-300 font-medium mb-1.5">
                Email Address
              </label>
              <Input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="developer@example.com"
                leftIcon={<Mail className="w-4 h-4 text-slate-500" />}
              />
            </div>

            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="block text-slate-300 font-medium">
                  Password
                </label>
              </div>
              <Input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••••••"
                leftIcon={<Lock className="w-4 h-4 text-slate-500" />}
              />
            </div>

            <div className="pt-2">
              <Button
                type="submit"
                isLoading={isSubmitting}
                variant="primary"
                size="md"
                fullWidth={true}
                rightIcon={<ArrowRight className="w-4 h-4" />}
              >
                Sign In
              </Button>
            </div>
          </form>

          <div className="pt-2 text-center text-xs text-slate-400 border-t border-[#252D46]">
            Don&apos;t have an account?{' '}
            <Link
              to="/register"
              className="text-[#A78BFA] hover:text-white font-medium hover:underline transition-colors"
            >
              Sign up
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};
