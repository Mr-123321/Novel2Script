'use client';

import * as React from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface SelectOption {
  value: string;
  label: string;
}

interface SelectProps
  extends Omit<React.SelectHTMLAttributes<HTMLSelectElement>, 'onChange'> {
  options: SelectOption[];
  placeholder?: string;
  onChange?: (value: string) => void;
}

export function Select({
  options,
  placeholder,
  className,
  onChange,
  value,
  ...props
}: SelectProps) {
  const handleChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    onChange?.(e.target.value);
  };

  return (
    <div className="relative">
      <select
        value={value}
        onChange={handleChange}
        className={cn(
          'flex h-10 w-full rounded-lg border border-white/10 bg-white/[0.04] px-3 py-2',
          'text-sm text-foreground',
          'appearance-none cursor-pointer',
          'focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500/30',
          'transition-colors duration-200',
          'hover:border-white/20',
          !value && 'text-muted-foreground',
          className
        )}
        style={{ backgroundColor: 'oklch(0.17 0.012 250)', color: 'oklch(0.94 0.005 250)' }}
        {...props}
      >
        {placeholder && (
          <option value="" disabled style={{ backgroundColor: '#1a1a2e', color: '#ccc' }}>
            {placeholder}
          </option>
        )}
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}
            style={{ backgroundColor: '#1a1a2e', color: '#e0e0e0' }}>
            {opt.label}
          </option>
        ))}
      </select>
      <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
    </div>
  );
}
