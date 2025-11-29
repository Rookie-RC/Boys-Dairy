import React, { useState, useEffect } from 'react';
import { Lock, Unlock, Calculator } from 'lucide-react';
import { UserSettings } from '../types';

interface LockScreenProps {
  settings: UserSettings;
  onUnlock: () => void;
  onSetPin: (pin: string) => void;
}

const LockScreen: React.FC<LockScreenProps> = ({ settings, onUnlock, onSetPin }) => {
  const [input, setInput] = useState('');
  const [isSetup, setIsSetup] = useState(!settings.pin);
  const [error, setError] = useState('');

  const handlePress = (val: string) => {
    if (input.length < 4) {
      const next = input + val;
      setInput(next);
      setError('');
    }
  };

  const handleDelete = () => {
    setInput(input.slice(0, -1));
    setError('');
  };

  const handleSubmit = () => {
    if (isSetup) {
      if (input.length === 4) {
        onSetPin(input);
        setIsSetup(false);
        setInput('');
        onUnlock(); // Auto unlock after setup
      } else {
        setError('PIN must be 4 digits');
      }
    } else {
      if (input === settings.pin) {
        onUnlock();
      } else {
        setError('Invalid PIN');
        setInput('');
      }
    }
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-zen-950 text-zen-200 p-4">
      <div className="mb-8 p-4 bg-zen-900 rounded-full shadow-lg border border-zen-800">
        {isSetup ? <Unlock size={32} className="text-accent-500" /> : <Calculator size={32} className="text-zen-400" />}
      </div>
      
      <h1 className="text-xl font-light tracking-widest mb-2 uppercase text-zen-400">
        {isSetup ? 'Set Access Code' : 'Zen Calculator'}
      </h1>
      
      <div className="h-4 mb-8 flex gap-2">
        {[0, 1, 2, 3].map((i) => (
          <div 
            key={i} 
            className={`w-3 h-3 rounded-full border transition-colors duration-200 ${
              input.length > i 
                ? 'bg-accent-500 border-accent-500' 
                : 'border-zen-600'
            }`}
          />
        ))}
      </div>

      {error && <p className="text-red-400 text-xs mb-4 h-4">{error}</p>}

      <div className="grid grid-cols-3 gap-6 w-full max-w-xs">
        {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((num) => (
          <button
            key={num}
            onClick={() => handlePress(num.toString())}
            className="w-16 h-16 rounded-full bg-zen-900 hover:bg-zen-800 text-xl font-light border border-zen-800 flex items-center justify-center transition-all active:scale-95 shadow-md"
          >
            {num}
          </button>
        ))}
        <button className="w-16 h-16 flex items-center justify-center text-sm font-light text-zen-500 hover:text-white" onClick={() => setInput('')}>CLR</button>
        <button
            onClick={() => handlePress('0')}
            className="w-16 h-16 rounded-full bg-zen-900 hover:bg-zen-800 text-xl font-light border border-zen-800 flex items-center justify-center transition-all active:scale-95 shadow-md"
          >
            0
        </button>
        <button className="w-16 h-16 flex items-center justify-center text-sm font-bold text-accent-500" onClick={handleSubmit}>
           ENT
        </button>
      </div>
    </div>
  );
};

export default LockScreen;