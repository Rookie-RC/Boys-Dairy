
export type Mood = 'Anxious' | 'Bored' | 'Excited' | 'Stressed' | 'Relaxed' | 'Guilty' | 'Empty' | 'Energetic' | 'Neutral';
export type ActivityType = 'solo' | 'partner' | 'urge';
export type Stimuli = 'imagination' | 'video' | 'audio' | 'reading' | 'partner' | 'none';
export type Volume = 'low' | 'medium' | 'high';

export interface LogEntry {
  id: string;
  timestamp: number; // Unix timestamp
  durationSeconds: number;
  type: ActivityType;
  stimuli: Stimuli | Stimuli[]; // Support legacy array, new single
  moodPre: Mood;
  moodPost: Mood;
  pleasureRating: number; // 1-10
  volume?: Volume; // New field replacing orgasm
  orgasm?: boolean; // Legacy field
  journal: string; // "Post-Clarity" notes
  pinned?: boolean;
  // Context for Analysis
  sleepQuality?: number; // 1-5 (1=Poor, 5=Great)
  stressLevel?: number; // 1-5 (1=Low, 5=High)
}

export type Language = 'en' | 'cn';
export type Theme = 'dark' | 'light';

export interface UserSettings {
  pin: string | null;
  username: string;
  language: Language;
  theme: Theme;
  biometricsEnabled: boolean;
  appIcon: 'calculator' | 'calendar' | 'weather';
}

export interface AppData {
  logs: LogEntry[];
  settings: UserSettings;
}

export enum AppView {
  LOCKED = 'LOCKED',
  DASHBOARD = 'DASHBOARD', // Tab 1: Home/Calendar
  HISTORY = 'HISTORY',     // Tab 2: List/Filter
  LOGGER = 'LOGGER',       // Tab 3: Add (+)
  ANALYTICS = 'ANALYTICS', // Tab 4: Stats
  SETTINGS = 'SETTINGS',   // Tab 5: Config
  BREATHING = 'BREATHING', // Sub-view
  JOURNAL = 'JOURNAL'      // Sub-view
}
