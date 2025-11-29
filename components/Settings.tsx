
import React, { useState } from 'react';
import { Moon, Sun, Globe, Lock, Fingerprint, Box, ArrowRight, ChevronRight, Calculator, Calendar, CloudSun, X, Check } from 'lucide-react';
import { UserSettings, Language, Theme } from '../types';

interface SettingsProps {
  settings: UserSettings;
  onUpdate: (newSettings: UserSettings) => void;
}

const t = {
  en: {
    title: "Settings",
    appearance: "Appearance",
    theme: "Theme",
    darkMode: "Dark Mode",
    lightMode: "Light Mode",
    language: "Language",
    security: "Security",
    setPin: "Set Password",
    changePin: "Change Password",
    pinDesc: "Protect your data",
    biometrics: "FaceID / TouchID",
    disguise: "App Disguise",
    icon: "App Icon",
    icons: { calc: "Calculator", cal: "Calendar", weather: "Weather" },
    modalTitle: "Configure Access",
    enterPin: "Enter 4-digit PIN",
    cancel: "Cancel",
    save: "Save"
  },
  cn: {
    title: "设置",
    appearance: "外观",
    theme: "主题",
    darkMode: "深色模式",
    lightMode: "浅色模式",
    language: "语言",
    security: "安全",
    setPin: "设置密码",
    changePin: "修改密码",
    pinDesc: "保护您的数据",
    biometrics: "生物识别 (FaceID)",
    disguise: "应用伪装",
    icon: "桌面图标",
    icons: { calc: "计算器", cal: "日历", weather: "天气" },
    modalTitle: "安全设置",
    enterPin: "输入4位数字密码",
    cancel: "取消",
    save: "保存"
  }
};

const Settings: React.FC<SettingsProps> = ({ settings, onUpdate }) => {
  const [showPinModal, setShowPinModal] = useState(false);
  const [newPin, setNewPin] = useState('');
  
  const lang = settings.language;
  const txt = t[lang];

  const updateSetting = <K extends keyof UserSettings>(key: K, value: UserSettings[K]) => {
    onUpdate({ ...settings, [key]: value });
  };

  const handlePinSubmit = () => {
    if (newPin.length === 4) {
      updateSetting('pin', newPin);
      setShowPinModal(false);
      setNewPin('');
    }
  };

  return (
    <div className="pb-24 pt-6 px-6 h-full overflow-y-auto no-scrollbar animate-fade-in text-zen-900 dark:text-zen-100 relative">
      <h1 className="text-2xl font-light mb-8">{txt.title}</h1>

      {/* Appearance */}
      <section className="mb-8">
        <h2 className="text-xs font-bold text-zen-500 uppercase tracking-widest mb-4 px-2">{txt.appearance}</h2>
        <div className="bg-white dark:bg-zen-900 rounded-2xl border border-zen-200 dark:border-zen-800 overflow-hidden">
            
            {/* Theme Toggle */}
            <div className="p-4 flex items-center justify-between border-b border-zen-100 dark:border-zen-800">
                <div className="flex items-center gap-3">
                    <div className="p-2 bg-zen-100 dark:bg-zen-800 rounded-lg">
                        {settings.theme === 'dark' ? <Moon size={18} /> : <Sun size={18} />}
                    </div>
                    <span className="text-sm font-medium">{settings.theme === 'dark' ? txt.darkMode : txt.lightMode}</span>
                </div>
                <button 
                  onClick={() => updateSetting('theme', settings.theme === 'dark' ? 'light' : 'dark')}
                  className={`w-12 h-6 rounded-full p-1 transition-colors ${settings.theme === 'dark' ? 'bg-accent-600' : 'bg-zen-300'}`}
                >
                  <div className={`w-4 h-4 bg-white rounded-full transition-transform ${settings.theme === 'dark' ? 'translate-x-6' : 'translate-x-0'}`} />
                </button>
            </div>

            {/* Language */}
            <div className="p-4 flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <div className="p-2 bg-zen-100 dark:bg-zen-800 rounded-lg">
                        <Globe size={18} />
                    </div>
                    <span className="text-sm font-medium">{txt.language}</span>
                </div>
                <div className="flex bg-zen-100 dark:bg-zen-800 rounded-lg p-1">
                    <button 
                      onClick={() => updateSetting('language', 'en')}
                      className={`px-3 py-1 rounded-md text-xs font-medium transition-all ${settings.language === 'en' ? 'bg-white dark:bg-zen-700 shadow-sm' : 'text-zen-500'}`}
                    >
                      EN
                    </button>
                    <button 
                      onClick={() => updateSetting('language', 'cn')}
                      className={`px-3 py-1 rounded-md text-xs font-medium transition-all ${settings.language === 'cn' ? 'bg-white dark:bg-zen-700 shadow-sm' : 'text-zen-500'}`}
                    >
                      中文
                    </button>
                </div>
            </div>
        </div>
      </section>

      {/* Security */}
      <section className="mb-8">
        <h2 className="text-xs font-bold text-zen-500 uppercase tracking-widest mb-4 px-2">{txt.security}</h2>
        <div className="bg-white dark:bg-zen-900 rounded-2xl border border-zen-200 dark:border-zen-800 overflow-hidden">
            
            {/* Set/Change PIN Button */}
            <div className="p-4">
                <button onClick={() => setShowPinModal(true)} className="w-full flex items-center justify-between group">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-zen-100 dark:bg-zen-800 rounded-lg group-hover:bg-accent-50 dark:group-hover:bg-accent-900/20 transition-colors">
                            <Lock size={18} className="group-hover:text-accent-500 transition-colors" />
                        </div>
                        <div className="text-left">
                           <span className="block text-sm font-medium">{settings.pin ? txt.changePin : txt.setPin}</span>
                           <span className="block text-xs text-zen-400">{txt.pinDesc}</span>
                        </div>
                    </div>
                    <ChevronRight size={16} className="text-zen-400" />
                </button>
            </div>
            
            {/* Biometrics Toggle */}
            <div className="p-4 flex items-center justify-between border-t border-zen-100 dark:border-zen-800">
                <div className="flex items-center gap-3">
                    <div className="p-2 bg-zen-100 dark:bg-zen-800 rounded-lg">
                        <Fingerprint size={18} />
                    </div>
                    <span className="text-sm font-medium">{txt.biometrics}</span>
                </div>
                <button 
                  onClick={() => updateSetting('biometricsEnabled', !settings.biometricsEnabled)}
                  className={`w-12 h-6 rounded-full p-1 transition-colors ${settings.biometricsEnabled ? 'bg-green-500' : 'bg-zen-300'}`}
                >
                  <div className={`w-4 h-4 bg-white rounded-full transition-transform ${settings.biometricsEnabled ? 'translate-x-6' : 'translate-x-0'}`} />
                </button>
            </div>
        </div>
      </section>

      {/* Disguise */}
      <section className="mb-8">
        <h2 className="text-xs font-bold text-zen-500 uppercase tracking-widest mb-4 px-2">{txt.disguise}</h2>
        <div className="bg-white dark:bg-zen-900 rounded-2xl border border-zen-200 dark:border-zen-800 p-4">
             <div className="flex items-center gap-3 mb-4">
                <div className="p-2 bg-zen-100 dark:bg-zen-800 rounded-lg">
                    <Box size={18} />
                </div>
                <span className="text-sm font-medium">{txt.icon}</span>
            </div>
            
            <div className="grid grid-cols-3 gap-3">
                {[
                    { id: 'calculator', label: txt.icons.calc, icon: Calculator },
                    { id: 'calendar', label: txt.icons.cal, icon: Calendar },
                    { id: 'weather', label: txt.icons.weather, icon: CloudSun },
                ].map(item => (
                    <button 
                        key={item.id}
                        onClick={() => updateSetting('appIcon', item.id as any)}
                        className={`flex flex-col items-center gap-2 p-3 rounded-xl border transition-all ${settings.appIcon === item.id ? 'bg-accent-50 dark:bg-accent-900/20 border-accent-500 text-accent-600 dark:text-accent-400' : 'bg-zen-50 dark:bg-zen-950 border-zen-100 dark:border-zen-800 text-zen-500'}`}
                    >
                        <item.icon size={24} />
                        <span className="text-[10px] font-medium">{item.label}</span>
                    </button>
                ))}
            </div>
        </div>
      </section>
      
      <div className="text-center text-xs text-zen-400 mt-12 mb-6">
        Daily Calc v2.1 <br/>
        Privacy First Design
      </div>

      {/* PIN MODAL */}
      {showPinModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-6 bg-zen-950/80 backdrop-blur-sm animate-fade-in">
              <div className="bg-white dark:bg-zen-900 w-full max-w-sm rounded-2xl p-6 shadow-2xl border border-zen-200 dark:border-zen-800">
                  <div className="flex justify-between items-center mb-6">
                      <h3 className="text-lg font-light">{txt.modalTitle}</h3>
                      <button onClick={() => setShowPinModal(false)} className="text-zen-500 hover:text-zen-900 dark:hover:text-white">
                          <X size={20} />
                      </button>
                  </div>
                  
                  <div className="mb-6 text-center">
                      <p className="text-sm text-zen-500 mb-4">{txt.enterPin}</p>
                      <input 
                        autoFocus
                        type="number"
                        pattern="\d*"
                        maxLength={4}
                        value={newPin}
                        onChange={(e) => e.target.value.length <= 4 && setNewPin(e.target.value)}
                        className="w-40 text-center text-3xl tracking-[1em] bg-transparent border-b-2 border-accent-500 focus:outline-none py-2 text-zen-900 dark:text-white font-mono"
                      />
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                      <button onClick={() => setShowPinModal(false)} className="py-3 rounded-xl text-sm font-medium text-zen-500 hover:bg-zen-100 dark:hover:bg-zen-800">
                          {txt.cancel}
                      </button>
                      <button 
                        onClick={handlePinSubmit}
                        disabled={newPin.length !== 4}
                        className={`py-3 rounded-xl text-sm font-bold flex items-center justify-center gap-2 ${newPin.length === 4 ? 'bg-accent-600 text-white shadow-lg' : 'bg-zen-200 dark:bg-zen-800 text-zen-400 cursor-not-allowed'}`}
                      >
                          <Check size={16} />
                          {txt.save}
                      </button>
                  </div>
              </div>
          </div>
      )}
    </div>
  );
};

export default Settings;
