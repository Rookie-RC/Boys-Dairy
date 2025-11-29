
import { AppData, LogEntry, UserSettings } from '../types';

const STORAGE_KEY = 'zen_journal_data_v2';

const DEFAULT_DATA: AppData = {
  logs: [],
  settings: {
    pin: null,
    username: 'User',
    language: 'cn', // Default to Chinese as requested implies interest
    theme: 'dark',
    biometricsEnabled: false,
    appIcon: 'calculator'
  },
};

export const getAppData = (): AppData => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_DATA;
    const parsed = JSON.parse(raw);
    // Merge with defaults to ensure new fields exist for old users
    return {
      ...DEFAULT_DATA,
      ...parsed,
      settings: { ...DEFAULT_DATA.settings, ...parsed.settings }
    };
  } catch (e) {
    console.error("Failed to load data", e);
    return DEFAULT_DATA;
  }
};

export const saveAppData = (data: AppData): void => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  } catch (e) {
    console.error("Failed to save data", e);
  }
};

export const clearAppData = (): void => {
  localStorage.removeItem(STORAGE_KEY);
};

export const addLogEntry = (entry: LogEntry): void => {
  const data = getAppData();
  const updatedLogs = [entry, ...data.logs];
  saveAppData({ ...data, logs: updatedLogs });
};
