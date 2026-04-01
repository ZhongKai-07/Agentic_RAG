import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export const WOBBLY_RADIUS = '255px 15px 225px 15px / 15px 225px 15px 255px'
export const WOBBLY_RADIUS_MD = '15px 225px 15px 255px / 255px 15px 225px 15px'
