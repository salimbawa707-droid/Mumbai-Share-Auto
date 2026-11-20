/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';

export default function App() {
  return (
    <div className="w-full min-h-screen bg-[#F8FAFC] text-[#0F172A] flex flex-col items-center justify-center p-6">
      <div className="max-w-xl w-full bg-white border border-[#CBD5E1] rounded-2xl p-6 shadow-xl space-y-4 text-center">
        <div className="w-12 h-12 rounded-xl bg-[#12304A] text-white flex items-center justify-center mx-auto text-xl font-bold">
          🚗
        </div>
        <h1 className="text-2xl font-extrabold text-[#0F172A]">Mumbai Share Auto</h1>
        <p className="text-sm text-[#475569] leading-relaxed">
          The complete application is the native Android app built with Kotlin and Jetpack Compose. This page is an informational web placeholder only; web-based booking, live driver matching, and passenger-to-driver tracking are not implemented here.
        </p>
        <div className="pt-2">
          <span className="inline-block px-3 py-1 bg-[#ECFDF5] text-[#14532D] text-xs font-semibold rounded-full border border-[#A7F3D0]">
            Native Android app source
          </span>
        </div>
      </div>
    </div>
  );
}
