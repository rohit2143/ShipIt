import React from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface SkeletonProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'rectangular' | 'circular' | 'text';
}

export const Skeleton: React.FC<SkeletonProps> = ({
  className,
  variant = 'rectangular',
  ...props
}) => {
  const variants = {
    rectangular: 'rounded-lg',
    circular: 'rounded-full',
    text: 'rounded h-3.5 w-full',
  };

  return (
    <div
      className={twMerge(
        clsx(
          'animate-shimmer rounded-lg border border-[#252D46]/40',
          variants[variant],
          className
        )
      )}
      {...props}
    />
  );
};
