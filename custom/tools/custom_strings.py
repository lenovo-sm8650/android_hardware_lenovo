#!/usr/bin/env python3
#
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
# Generates TB520FUCustomFeatures/res/values*/strings.xml for the "Custom
# Tweaks" app of the optional TB520FU customizations (game performance and
# the apps that see the Play Store as their installer). English is the default; strings equal to English
# are left out of the translations so they fall back to it.
import os, re

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                   'TB520FUCustomFeatures', 'res')

KEYS = [
 ('app_name', None), ('app_summary', None),
 ('game_perf_title', None), ('game_perf_summary', None),
 ('game_perf_on', None), ('game_perf_off', None),
 ('game_perf_main_switch', None),
 ('game_mem_clean_title', None), ('game_mem_clean_summary', None),
 ('game_apps_category', None), ('game_app_add', None), ('game_app_remove', None),
 ('game_app_levels', None),
 ('game_cpu_category', None), ('game_gpu_category', None),
 ('game_perf_footer', None),
 ('game_custom_cpu_title', None),
 ('game_custom_gpu_title', None), ('game_custom_range_summary', None),
 ('game_custom_cpu_summary', None), ('game_custom_gpu_summary', None),
 ('game_custom_level', None),
 ('play_store_category', 'Play Store'),
 ('installer_spoof_title', None), ('installer_spoof_footer', None),
 ('installer_spoof_remove_message', None),
 ('app_compat_category', None),
 ('device_spoof_title', None),
 ('device_spoof_main_switch', None),
 ('device_spoof_device_category', None),
 ('device_spoof_brand', None),
 ('device_spoof_manufacturer', None),
 ('device_spoof_model', None),
 ('device_spoof_unchanged', None),
 ('device_spoof_not_installed', None),
 ('device_spoof_summary', None),
 ('device_spoof_reset', None),
 ('device_spoof_reset_summary', None),
 ('device_spoof_reset_message', None),
 ('device_spoof_remove_message', None),
 ('device_spoof_footer', None),
]
ARRAYS = ['game_level_entries', 'game_cpu_level_summaries', 'game_gpu_level_summaries']

L = {}
L['en'] = dict(
 app_name='Custom Tweaks',
 app_summary='Game performance tuning, Play Store installer reporting',
 game_perf_title='Game performance',
 game_perf_on='On',
 game_perf_off='Off',
 game_perf_main_switch='Use per-app CPU and GPU settings',
 game_mem_clean_title='Free memory for games',
 game_mem_clean_summary='Closes background apps when an app from the list opens',
 game_apps_category='Apps',
 game_app_add='Add app',
 game_app_remove='Remove from list',
 game_app_levels='CPU %1$s · GPU %2$s',
 game_cpu_category='CPU',
 game_gpu_category='GPU',
 game_perf_footer='Settings apply only while the app is on screen. Clocks return to normal when you leave the app. Thermal protection always stays active.',
 game_custom_cpu_title='CPU clock',
 game_custom_gpu_title='GPU limit',
 game_custom_range_summary='30–100 %',
 game_custom_cpu_summary='Custom %1$d%%',
 game_custom_gpu_summary='Custom %1$d%%',
 game_custom_level='Custom',
 play_store_category='Play Store',
 installer_spoof_title='Report the Play Store as the installer',
 installer_spoof_footer='Only the apps in this list see the Play Store as their own installer, so apps that require a Play install (for example Notein) keep working. Other apps and the Play Store still see the real installer. Applies immediately.',
 installer_spoof_remove_message='This app will see its real installer again.',
 app_compat_category='App compatibility',
 device_spoof_title='Device identity for apps',
 device_spoof_main_switch='Show another device to selected apps',
 device_spoof_device_category='Device',
 device_spoof_brand='Brand',
 device_spoof_manufacturer='Manufacturer',
 device_spoof_model='Model',
 device_spoof_unchanged='Not changed',
 device_spoof_not_installed='%1$s (not installed)',
 device_spoof_summary='%1$s · %2$s',
 device_spoof_reset='Reset to default',
 device_spoof_reset_summary='OnePlus Pad Go 2 for Netflix',
 device_spoof_reset_message='The device, the apps and the switch go back to their defaults.',
 device_spoof_remove_message='This app will see the real device again.',
 device_spoof_footer='Only the apps in this list see this brand, manufacturer and model instead of the real device, for example so that Netflix streams HDR10, HDR10+ and Dolby Vision. Google Play services and the Play Store always see the real device. Restart the app to apply a change.',
 game_perf_summary='CPU and GPU profiles per app; settings apply only while the app is on screen',

 game_level_entries=['Power saving', 'Balanced', 'Default'],


 game_cpu_level_summaries=['Lower clocks for less heat and longer play time', 'Caps the single core and the other cores to about 80 % for steady long sessions', 'Stock clocks; thermal protection still applies', 'Set the single core and multi core limits yourself'],

 game_gpu_level_summaries=['Lower graphics clock for less heat', 'Caps the graphics clock to about 80 % for less heat in long sessions', 'Stock graphics clock', 'Set the graphics clock limit yourself'],

)
L['ko'] = dict(
 app_name='커스텀 트윅',
 app_summary='게임 성능 조절, Play 스토어 설치자 표시',
 game_perf_title='게임 성능 관리',
 game_perf_on='사용',
 game_perf_off='사용 안함',
 game_perf_main_switch='앱별 CPU·GPU 설정 사용',
 game_mem_clean_title='게임용 메모리 정리',
 game_mem_clean_summary='목록의 앱을 열 때 백그라운드 앱을 종료합니다',
 game_apps_category='앱',
 game_app_add='앱 추가',
 game_app_remove='목록에서 삭제',
 game_app_levels='CPU %1$s · GPU %2$s',
 game_cpu_category='CPU',
 game_gpu_category='GPU',
 game_perf_footer='설정은 해당 앱이 화면에 있을 때만 적용됩니다. 앱에서 나가면 클럭이 원래대로 돌아옵니다. 발열 보호는 항상 동작합니다.',
 game_custom_cpu_title='CPU 클럭',
 game_custom_gpu_title='GPU 제한',
 game_custom_range_summary='30~100%',
 game_custom_cpu_summary='사용자 설정 %1$d%%',
 game_custom_gpu_summary='사용자 설정 %1$d%%',
 game_custom_level='사용자 설정',
 play_store_category='Play 스토어',
 installer_spoof_title='Play 스토어에서 설치한 것으로 표시',
 installer_spoof_footer='이 목록의 앱만 자기 설치자를 Play 스토어로 인식합니다. Play 설치가 필요한 앱(예: Notein)이 작동합니다. 다른 앱과 Play 스토어에는 실제 설치자가 그대로 보입니다. 즉시 적용됩니다.',
 installer_spoof_remove_message='이 앱은 다시 실제 설치자를 보게 됩니다.',
 app_compat_category='앱 호환성',
 device_spoof_title='앱별 기기 정보',
 device_spoof_main_switch='선택한 앱에 다른 기기로 표시',
 device_spoof_device_category='기기',
 device_spoof_brand='브랜드',
 device_spoof_manufacturer='제조사',
 device_spoof_model='모델',
 device_spoof_unchanged='변경 안 함',
 device_spoof_not_installed='%1$s (설치되지 않음)',
 device_spoof_summary='%1$s · %2$s',
 device_spoof_reset='기본값으로 재설정',
 device_spoof_reset_summary='넷플릭스에 OnePlus Pad Go 2로 표시',
 device_spoof_reset_message='기기, 앱 목록, 스위치가 기본값으로 돌아갑니다.',
 device_spoof_remove_message='이 앱은 다시 실제 기기 정보를 보게 됩니다.',
 device_spoof_footer='이 목록의 앱에만 실제 기기 대신 위의 브랜드, 제조사, 모델이 보입니다. 예를 들어 넷플릭스에서 HDR10, HDR10+, Dolby Vision으로 재생됩니다. Google Play 서비스와 Play 스토어에는 항상 실제 기기가 보입니다. 바꾼 설정은 앱을 다시 시작하면 적용됩니다.',
 game_perf_summary='앱별 CPU·GPU 설정입니다. 설정은 해당 앱이 화면에 있을 때만 적용됩니다',

 game_level_entries=['절전', '균형', '기본값'],


 game_cpu_level_summaries=['클럭을 낮춰 발열을 줄이고 플레이 시간을 늘립니다', '싱글 코어와 나머지 코어를 약 80%로 제한해 오래 플레이해도 안정적입니다', '순정 클럭을 사용합니다 (발열 보호는 유지)', '싱글·멀티 코어 제한을 직접 설정합니다'],

 game_gpu_level_summaries=['그래픽 클럭을 낮춰 발열을 줄입니다', '그래픽 클럭을 약 80%로 제한해 오래 플레이할 때 발열을 줄입니다', '순정 그래픽 클럭을 사용합니다', 'GPU 클럭 제한을 직접 설정합니다'],

)
L['ja'] = dict(
 app_name='Custom Tweaks',
 app_summary='ゲームパフォーマンス調整、Play ストアのインストーラー表示',
 game_perf_title='ゲームパフォーマンス',
 game_perf_on='オン',
 game_perf_off='オフ',
 game_perf_main_switch='アプリ別のパフォーマンスプロファイルを使用',
 game_mem_clean_title='ゲーム用にメモリを解放',
 game_mem_clean_summary='リストのアプリを開くとバックグラウンドアプリを終了します',
 game_apps_category='アプリ',
 game_app_add='アプリを追加',
 game_app_remove='削除',
 game_app_levels='CPU %1$s · GPU %2$s',
 game_cpu_category='CPU',
 game_gpu_category='GPU',
 game_perf_footer='プロファイルはそのアプリが画面に表示されている間だけ適用されます。アプリを離れるとクロックは元に戻ります。熱保護は常に有効です。',
 game_custom_cpu_title='CPUクロック',
 game_custom_gpu_title='GPU制限',
 game_custom_range_summary='30～100%',
 game_custom_cpu_summary='カスタム %1$d%%',
 game_custom_gpu_summary='カスタム %1$d%%',
 game_custom_level='カスタム',
 play_store_category='Play ストア',
 game_perf_summary='アプリ別のCPU・GPU設定です。設定はアプリが画面に表示されている間だけ適用されます',

 game_level_entries=['省電力', 'バランス', 'デフォルト'],

 game_cpu_level_summaries=['クロックを下げて発熱を抑え、プレイ時間を延ばします', 'シングルコアとその他のコアを約80%に制限し、長時間でも安定させます', '標準のクロックを使用します（熱保護は有効）', 'シングルコアとマルチコアの制限を自分で設定します'],

 game_gpu_level_summaries=['グラフィッククロックを下げて発熱を抑えます', 'グラフィッククロックを約80%に制限し、長時間でも安定させます', '標準のグラフィッククロックを使用します', 'グラフィッククロックの制限を自分で設定します'],

 installer_spoof_title='Play ストアからのインストールとして表示',
 installer_spoof_footer='このリストのアプリだけが、自分のインストーラーを Play ストアとして認識します。Play からのインストールが必要なアプリ（例: Notein）が動作します。他のアプリと Play ストアには実際のインストーラーが表示されます。すぐに適用されます。',
 installer_spoof_remove_message='このアプリには実際のインストーラーが再び表示されます。',
 app_compat_category='アプリの互換性',
 device_spoof_title='アプリごとのデバイス情報',
 device_spoof_main_switch='選択したアプリに別のデバイスとして表示',
 device_spoof_device_category='デバイス',
 device_spoof_brand='ブランド',
 device_spoof_manufacturer='メーカー',
 device_spoof_model='モデル',
 device_spoof_unchanged='変更しない',
 device_spoof_not_installed='%1$s（未インストール）',
 device_spoof_summary='%1$s · %2$s',
 device_spoof_reset='デフォルトに戻す',
 device_spoof_reset_summary='Netflix に OnePlus Pad Go 2 として表示',
 device_spoof_reset_message='デバイス、アプリのリスト、スイッチがデフォルトに戻ります。',
 device_spoof_remove_message='このアプリには再び実際のデバイスが表示されます。',
 device_spoof_footer='このリストのアプリにだけ、実際のデバイスの代わりに上のブランド、メーカー、モデルが表示されます。たとえば Netflix で HDR10、HDR10+、Dolby Vision が再生されます。Google Play 開発者サービスと Play ストアには常に実際のデバイスが表示されます。変更はアプリを再起動すると適用されます。',
)
L['zh-rCN'] = dict(
 app_name='Custom Tweaks',
 app_summary='游戏性能调节、Play 商店安装来源显示',
 game_perf_title='游戏性能',
 game_perf_on='开启',
 game_perf_off='关闭',
 game_perf_main_switch='使用应用性能配置',
 game_mem_clean_title='游戏内存清理',
 game_mem_clean_summary='打开列表中的应用时关闭后台应用',
 game_apps_category='应用',
 game_app_add='添加应用',
 game_app_remove='移除',
 game_app_levels='CPU %1$s · GPU %2$s',
 game_cpu_category='CPU',
 game_gpu_category='GPU',
 game_perf_footer='配置仅在对应应用显示在屏幕上时生效，离开应用后频率恢复正常。温控保护始终有效。',
 game_custom_cpu_title='CPU 频率',
 game_custom_gpu_title='GPU 限制',
 game_custom_range_summary='30～100%',
 game_custom_cpu_summary='自定义 %1$d%%',
 game_custom_gpu_summary='自定义 %1$d%%',
 game_custom_level='自定义',
 play_store_category='Play 商店',
 game_perf_summary='按应用设置CPU和GPU；设置仅在该应用显示时生效',

 game_level_entries=['省电', '均衡', '默认'],

 game_cpu_level_summaries=['降低频率，减少发热，延长游戏时间', '将单核和其他核心限制在约 80%，长时间游戏更稳定', '使用原厂频率（温控仍然有效）', '自行设置单核和多核限制'],

 game_gpu_level_summaries=['降低图形频率，减少发热', '将图形频率限制在约 80%，长时间更稳定', '使用原厂图形频率', '自行设置图形频率限制'],

 installer_spoof_title='将安装来源显示为 Play 商店',
 installer_spoof_footer='只有此列表中的应用会将自己的安装来源识别为 Play 商店，因此需要 Play 安装的应用（例如 Notein）可以正常工作。其他应用和 Play 商店仍看到真实的安装来源。立即生效。',
 installer_spoof_remove_message='此应用将重新看到真实的安装来源。',
 app_compat_category='应用兼容性',
 device_spoof_title='应用的设备信息',
 device_spoof_main_switch='向所选应用显示为其他设备',
 device_spoof_device_category='设备',
 device_spoof_brand='品牌',
 device_spoof_manufacturer='制造商',
 device_spoof_model='型号',
 device_spoof_unchanged='不更改',
 device_spoof_not_installed='%1$s（未安装）',
 device_spoof_summary='%1$s · %2$s',
 device_spoof_reset='恢复默认设置',
 device_spoof_reset_summary='向 Netflix 显示为 OnePlus Pad Go 2',
 device_spoof_reset_message='设备、应用列表和开关将恢复为默认设置。',
 device_spoof_remove_message='此应用将重新看到实际设备。',
 device_spoof_footer='只有此列表中的应用会看到上面的品牌、制造商和型号，而不是实际设备，例如 Netflix 会播放 HDR10、HDR10+ 和杜比视界。Google Play 服务和 Play 商店始终看到实际设备。更改在重新启动应用后生效。',
)
L['zh-rTW'] = dict(
 app_name='Custom Tweaks',
 app_summary='遊戲效能調整、Play 商店安裝來源顯示',
 game_perf_title='遊戲效能',
 game_perf_on='開啟',
 game_perf_off='關閉',
 game_perf_main_switch='使用應用程式效能設定檔',
 game_mem_clean_title='遊戲記憶體清理',
 game_mem_clean_summary='開啟清單中的應用程式時關閉背景應用程式',
 game_apps_category='應用程式',
 game_app_add='新增應用程式',
 game_app_remove='移除',
 game_app_levels='CPU %1$s · GPU %2$s',
 game_cpu_category='CPU',
 game_gpu_category='GPU',
 game_perf_footer='設定檔僅在該應用程式顯示於螢幕上時生效，離開後時脈恢復正常。溫度保護始終有效。',
 game_custom_cpu_title='CPU 時脈',
 game_custom_gpu_title='GPU 限制',
 game_custom_range_summary='30～100%',
 game_custom_cpu_summary='自訂 %1$d%%',
 game_custom_gpu_summary='自訂 %1$d%%',
 game_custom_level='自訂',
 play_store_category='Play 商店',
 game_perf_summary='依應用設定CPU與GPU；設定僅在該應用顯示時生效',

 game_level_entries=['省電', '平衡', '預設'],

 game_cpu_level_summaries=['降低時脈，減少發熱，延長遊戲時間', '將單核與其他核心限制在約 80%，長時間遊戲更穩定', '使用原廠時脈（溫度保護仍有效）', '自行設定單核與多核限制'],

 game_gpu_level_summaries=['降低圖形時脈，減少發熱', '將圖形時脈限制在約 80%，長時間更穩定', '使用原廠圖形時脈', '自行設定圖形時脈限制'],

 installer_spoof_title='將安裝來源顯示為 Play 商店',
 installer_spoof_footer='只有此清單中的應用程式會將自己的安裝來源識別為 Play 商店，因此需要 Play 安裝的應用程式（例如 Notein）可以正常運作。其他應用程式和 Play 商店仍看到真實的安裝來源。立即生效。',
 installer_spoof_remove_message='此應用程式將重新看到真實的安裝來源。',
 app_compat_category='應用程式相容性',
 device_spoof_title='應用程式的裝置資訊',
 device_spoof_main_switch='向所選應用程式顯示為其他裝置',
 device_spoof_device_category='裝置',
 device_spoof_brand='品牌',
 device_spoof_manufacturer='製造商',
 device_spoof_model='型號',
 device_spoof_unchanged='不變更',
 device_spoof_not_installed='%1$s（未安裝）',
 device_spoof_summary='%1$s · %2$s',
 device_spoof_reset='還原預設值',
 device_spoof_reset_summary='向 Netflix 顯示為 OnePlus Pad Go 2',
 device_spoof_reset_message='裝置、應用程式清單和開關將還原為預設值。',
 device_spoof_remove_message='此應用程式將重新看到實際裝置。',
 device_spoof_footer='只有此清單中的應用程式會看到上方的品牌、製造商和型號，而不是實際裝置，例如 Netflix 會播放 HDR10、HDR10+ 和杜比視界。Google Play 服務和 Play 商店一律看到實際裝置。變更會在重新啟動應用程式後生效。',
)
L['de'] = dict(
 app_name='Custom Tweaks',
 app_summary='Spielleistungsanpassung, Play-Store-Installationsquelle',
 game_perf_title='Spieleleistung',
 game_perf_on='An',
 game_perf_off='Aus',
 game_perf_main_switch='Leistungsprofile pro App verwenden',
 game_mem_clean_title='Speicher für Spiele freigeben',
 game_mem_clean_summary='Schließt Hintergrund-Apps, wenn eine App aus der Liste geöffnet wird',
 game_apps_category='Apps',
 game_app_add='App hinzufügen',
 game_app_remove='Entfernen',
 game_app_levels='CPU %1$s · GPU %2$s',
 game_cpu_category='CPU',
 game_gpu_category='GPU',
 game_perf_footer='Ein Profil gilt nur, solange seine App auf dem Bildschirm ist. Beim Verlassen der App kehren die Taktraten zum Normalwert zurück. Der Überhitzungsschutz bleibt immer aktiv.',
 game_custom_cpu_title='CPU-Takt',
 game_custom_gpu_title='GPU-Limit',
 game_custom_range_summary='30–100 %',
 game_custom_cpu_summary='Benutzerdefiniert %1$d%%',
 game_custom_gpu_summary='Benutzerdefiniert %1$d%%',
 game_custom_level='Benutzerdefiniert',
 play_store_category='Play Store',
 game_perf_summary='CPU- und GPU-Profile pro App; die Einstellungen gelten nur, solange die App sichtbar ist',

 game_level_entries=['Energiesparen', 'Ausgewogen', 'Standard'],

 game_cpu_level_summaries=['Niedrigere Taktraten für weniger Wärme und längere Spielzeit', 'Begrenzt den Einzelkern und die übrigen Kerne auf etwa 80 % für lange, stabile Sitzungen', 'Serienmäßige Taktraten; Überhitzungsschutz bleibt aktiv', 'Einzel- und Mehrkern-Limit selbst festlegen'],

 game_gpu_level_summaries=['Niedrigerer Grafiktakt für weniger Wärme', 'Begrenzter Grafiktakt auf etwa 80 % für lange, stabile Sitzungen', 'Serienmäßiger Grafiktakt', 'Grafiktakt-Limit selbst festlegen'],

 installer_spoof_title='Play Store als Installationsquelle melden',
 installer_spoof_footer='Nur die Apps in dieser Liste sehen den Play Store als ihre eigene Installationsquelle, damit Apps, die eine Play-Installation verlangen (zum Beispiel Notein), weiter funktionieren. Andere Apps und der Play Store sehen weiter die echte Installationsquelle. Gilt sofort.',
 installer_spoof_remove_message='Diese App sieht wieder ihre echte Installationsquelle.',
 app_compat_category='App-Kompatibilität',
 device_spoof_title='Geräteangaben für Apps',
 device_spoof_main_switch='Ausgewählten Apps ein anderes Gerät zeigen',
 device_spoof_device_category='Gerät',
 device_spoof_brand='Marke',
 device_spoof_manufacturer='Hersteller',
 device_spoof_model='Modell',
 device_spoof_unchanged='Nicht geändert',
 device_spoof_not_installed='%1$s (nicht installiert)',
 device_spoof_summary='%1$s · %2$s',
 device_spoof_reset='Auf Standard zurücksetzen',
 device_spoof_reset_summary='OnePlus Pad Go 2 für Netflix',
 device_spoof_reset_message='Gerät, Apps und Schalter werden auf die Standardwerte zurückgesetzt.',
 device_spoof_remove_message='Diese App sieht wieder das echte Gerät.',
 device_spoof_footer='Nur die Apps in dieser Liste sehen diese Marke, diesen Hersteller und dieses Modell statt des echten Geräts, zum Beispiel damit Netflix HDR10, HDR10+ und Dolby Vision streamt. Google Play-Dienste und der Play Store sehen immer das echte Gerät. Starte die App neu, damit eine Änderung wirkt.',
)
L['fr'] = dict(
 app_name='Custom Tweaks',
 app_summary='Réglage des performances de jeu, source d’installation Play Store',
 game_perf_title='Performances en jeu',
 game_perf_on='Activé',
 game_perf_off='Désactivé',
 game_perf_main_switch='Utiliser des profils de performances par application',
 game_mem_clean_title='Libérer la mémoire pour les jeux',
 game_mem_clean_summary="Ferme les applications en arrière-plan à l'ouverture d'une application de la liste",
 game_apps_category='Applications',
 game_app_add='Ajouter une application',
 game_app_remove='Retirer',
 game_app_levels='CPU %1$s · GPU %2$s',
 game_cpu_category='CPU',
 game_gpu_category='GPU',
 game_perf_footer="Un profil ne s'applique que lorsque son application est à l'écran. Les fréquences reviennent à la normale quand vous la quittez. La protection thermique reste toujours active.",
 game_custom_cpu_title='Fréquence du CPU',
 game_custom_gpu_title='Limite GPU',
 game_custom_range_summary='30–100 %',
 game_custom_cpu_summary='Personnalisé %1$d%%',
 game_custom_gpu_summary='Personnalisé %1$d%%',
 game_custom_level='Personnalisé',
 play_store_category='Play Store',
 game_perf_summary="Profils CPU et GPU par application ; les réglages ne s'appliquent que lorsque l'application est à l'écran",

 game_level_entries=["Économie d'énergie", 'Équilibré', 'Par défaut'],

 game_cpu_level_summaries=["Fréquences réduites pour moins de chaleur et plus d'autonomie", 'Limite le cœur principal et les autres cœurs à environ 80 % pour une fluidité durable', "Fréquences d'origine ; la protection thermique reste active", 'Définissez vous-même les limites simple et multiple'],

 game_gpu_level_summaries=['Fréquence graphique réduite pour moins de chaleur', 'Fréquence graphique limitée à environ 80 % pour une stabilité durable', "Fréquence graphique d'origine", 'Définissez vous-même la limite de fréquence graphique'],

 installer_spoof_title='Signaler le Play Store comme installateur',
 installer_spoof_footer='Seules les applis de cette liste voient le Play Store comme leur propre installateur, donc les applis qui exigent une installation Play (par exemple Notein) continuent de fonctionner. Les autres applis et le Play Store voient toujours le vrai installateur. S’applique immédiatement.',
 installer_spoof_remove_message='Cette appli verra de nouveau son vrai installateur.',
 app_compat_category='Compatibilité des applis',
 device_spoof_title="Identité de l'appareil pour les applis",
 device_spoof_main_switch='Montrer un autre appareil aux applis sélectionnées',
 device_spoof_device_category='Appareil',
 device_spoof_brand='Marque',
 device_spoof_manufacturer='Fabricant',
 device_spoof_model='Modèle',
 device_spoof_unchanged='Non modifié',
 device_spoof_not_installed='%1$s (non installée)',
 device_spoof_summary='%1$s · %2$s',
 device_spoof_reset='Rétablir les valeurs par défaut',
 device_spoof_reset_summary='OnePlus Pad Go 2 pour Netflix',
 device_spoof_reset_message="L'appareil, les applis et l'interrupteur reprennent leurs valeurs par défaut.",
 device_spoof_remove_message="Cette appli verra de nouveau l'appareil réel.",
 device_spoof_footer="Seules les applis de cette liste voient cette marque, ce fabricant et ce modèle au lieu de l'appareil réel, par exemple pour que Netflix diffuse en HDR10, HDR10+ et Dolby Vision. Les services Google Play et le Play Store voient toujours l'appareil réel. Redémarrez l'appli pour appliquer une modification.",
)
L['es'] = dict(
 app_name='Custom Tweaks',
 app_summary='Ajuste del rendimiento en juegos, instalador de Play Store',
 game_perf_title='Rendimiento en juegos',
 game_perf_on='Activado',
 game_perf_off='Desactivado',
 game_perf_main_switch='Usar perfiles de rendimiento por aplicación',
 game_mem_clean_title='Liberar memoria para juegos',
 game_mem_clean_summary='Cierra las aplicaciones en segundo plano al abrir una aplicación de la lista',
 game_apps_category='Aplicaciones',
 game_app_add='Añadir aplicación',
 game_app_remove='Quitar',
 game_app_levels='CPU %1$s · GPU %2$s',
 game_cpu_category='CPU',
 game_gpu_category='GPU',
 game_perf_footer='Un perfil solo se aplica mientras su aplicación está en pantalla. Las frecuencias vuelven a la normalidad al salir de la aplicación. La protección térmica siempre sigue activa.',
 game_custom_cpu_title='Frecuencia de la CPU',
 game_custom_gpu_title='Límite de GPU',
 game_custom_range_summary='30–100 %',
 game_custom_cpu_summary='Personalizado %1$d%%',
 game_custom_gpu_summary='Personalizado %1$d%%',
 game_custom_level='Personalizado',
 play_store_category='Play Store',
 game_perf_summary='Perfiles de CPU y GPU por aplicación; los ajustes solo se aplican mientras la aplicación está en pantalla',

 game_level_entries=['Ahorro de energía', 'Equilibrado', 'Predeterminado'],

 game_cpu_level_summaries=['Frecuencias más bajas para menos calor y más tiempo de juego', 'Limita el núcleo principal y los demás a un 80 % para sesiones largas estables', 'Frecuencias originales; la protección térmica sigue activa', 'Define tú mismo los límites de uno y varios núcleos'],

 game_gpu_level_summaries=['Frecuencia gráfica más baja para menos calor', 'Frecuencia gráfica limitada a un 80 % para sesiones largas estables', 'Frecuencia gráfica original', 'Define tú mismo el límite de frecuencia gráfica'],

 installer_spoof_title='Mostrar Play Store como instalador',
 installer_spoof_footer='Solo las apps de esta lista ven Play Store como su propio instalador, así que las apps que exigen una instalación de Play (por ejemplo Notein) siguen funcionando. Las demás apps y Play Store siguen viendo el instalador real. Se aplica al momento.',
 installer_spoof_remove_message='Esta app volverá a ver su instalador real.',
 app_compat_category='Compatibilidad de apps',
 device_spoof_title='Identidad del dispositivo para apps',
 device_spoof_main_switch='Mostrar otro dispositivo a las apps seleccionadas',
 device_spoof_device_category='Dispositivo',
 device_spoof_brand='Marca',
 device_spoof_manufacturer='Fabricante',
 device_spoof_model='Modelo',
 device_spoof_unchanged='Sin cambios',
 device_spoof_not_installed='%1$s (no instalada)',
 device_spoof_summary='%1$s · %2$s',
 device_spoof_reset='Restablecer valores predeterminados',
 device_spoof_reset_summary='OnePlus Pad Go 2 para Netflix',
 device_spoof_reset_message='El dispositivo, las apps y el interruptor vuelven a sus valores predeterminados.',
 device_spoof_remove_message='Esta app volverá a ver el dispositivo real.',
 device_spoof_footer='Solo las apps de esta lista ven esta marca, este fabricante y este modelo en lugar del dispositivo real, por ejemplo para que Netflix reproduzca HDR10, HDR10+ y Dolby Vision. Los servicios de Google Play y Play Store siempre ven el dispositivo real. Reinicia la app para aplicar un cambio.',
)
L['it'] = dict(
 app_name='Custom Tweaks',
 app_summary='Regolazione delle prestazioni di gioco, installatore Play Store',
 game_perf_title='Prestazioni di gioco',
 game_perf_on='Attivo',
 game_perf_off='Disattivato',
 game_perf_main_switch='Usa profili di prestazioni per app',
 game_mem_clean_title='Libera memoria per i giochi',
 game_mem_clean_summary="Chiude le app in background quando si apre un'app dell'elenco",
 game_apps_category='App',
 game_app_add='Aggiungi app',
 game_app_remove='Rimuovi',
 game_app_levels='CPU %1$s · GPU %2$s',
 game_cpu_category='CPU',
 game_gpu_category='GPU',
 game_perf_footer="Un profilo si applica solo mentre la sua app è sullo schermo. Le frequenze tornano normali quando esci dall'app. La protezione termica resta sempre attiva.",
 game_custom_cpu_title='Frequenza CPU',
 game_custom_gpu_title='Limite GPU',
 game_custom_range_summary='30–100%',
 game_custom_cpu_summary='Personalizzato %1$d%%',
 game_custom_gpu_summary='Personalizzato %1$d%%',
 game_custom_level='Personalizzato',
 play_store_category='Play Store',
 game_perf_summary="Profili CPU e GPU per app; le impostazioni valgono solo mentre l'app è sullo schermo",

 game_level_entries=['Risparmio energetico', 'Bilanciato', 'Predefinito'],

 game_cpu_level_summaries=['Frequenze più basse per meno calore e più tempo di gioco', "Limita il core principale e gli altri all'80% per sessioni lunghe stabili", 'Frequenze originali; la protezione termica resta attiva', 'Imposta tu i limiti singolo e multi core'],

 game_gpu_level_summaries=['Frequenza grafica più bassa per meno calore', "Frequenza grafica limitata all'80% per sessioni lunghe stabili", 'Frequenza grafica originale', 'Imposta tu il limite di frequenza grafica'],

 installer_spoof_title='Segnala Play Store come installatore',
 installer_spoof_footer='Solo le app in questo elenco vedono Play Store come proprio installatore, così le app che richiedono un’installazione Play (per esempio Notein) continuano a funzionare. Le altre app e Play Store vedono ancora l’installatore reale. Si applica subito.',
 installer_spoof_remove_message='Questa app vedrà di nuovo il suo installatore reale.',
 app_compat_category='Compatibilità app',
 device_spoof_title='Identità del dispositivo per le app',
 device_spoof_main_switch='Mostra un altro dispositivo alle app selezionate',
 device_spoof_device_category='Dispositivo',
 device_spoof_brand='Marca',
 device_spoof_manufacturer='Produttore',
 device_spoof_model='Modello',
 device_spoof_unchanged='Non modificato',
 device_spoof_not_installed='%1$s (non installata)',
 device_spoof_summary='%1$s · %2$s',
 device_spoof_reset='Ripristina predefiniti',
 device_spoof_reset_summary='OnePlus Pad Go 2 per Netflix',
 device_spoof_reset_message='Dispositivo, app e interruttore tornano ai valori predefiniti.',
 device_spoof_remove_message='Questa app vedrà di nuovo il dispositivo reale.',
 device_spoof_footer="Solo le app di questo elenco vedono questa marca, questo produttore e questo modello al posto del dispositivo reale, ad esempio perché Netflix trasmetta in HDR10, HDR10+ e Dolby Vision. Google Play Services e il Play Store vedono sempre il dispositivo reale. Riavvia l'app per applicare una modifica.",
)
L['pt-rBR'] = dict(
 app_name='Custom Tweaks',
 app_summary='Ajuste de desempenho em jogos, instalador da Play Store',
 game_perf_title='Desempenho em jogos',
 game_perf_on='Ativado',
 game_perf_off='Desativado',
 game_perf_main_switch='Usar perfis de desempenho por app',
 game_mem_clean_title='Liberar memória para jogos',
 game_mem_clean_summary='Fecha apps em segundo plano ao abrir um app da lista',
 game_apps_category='Apps',
 game_app_add='Adicionar app',
 game_app_remove='Remover',
 game_app_levels='CPU %1$s · GPU %2$s',
 game_cpu_category='CPU',
 game_gpu_category='GPU',
 game_perf_footer='Um perfil só se aplica enquanto o app está na tela. As frequências voltam ao normal quando você sai do app. A proteção térmica continua sempre ativa.',
 game_custom_cpu_title='Frequência da CPU',
 game_custom_gpu_title='Limite da GPU',
 game_custom_range_summary='30–100%',
 game_custom_cpu_summary='Personalizado %1$d%%',
 game_custom_gpu_summary='Personalizado %1$d%%',
 game_custom_level='Personalizado',
 play_store_category='Play Store',
 game_perf_summary='Perfis de CPU e GPU por app; as configurações valem só enquanto o app está na tela',

 game_level_entries=['Economia de energia', 'Equilibrado', 'Padrão'],

 game_cpu_level_summaries=['Frequências menores para menos calor e mais tempo de jogo', 'Limita o núcleo principal e os demais a cerca de 80% para sessões longas estáveis', 'Frequências originais; a proteção térmica continua ativa', 'Defina você mesmo os limites de um e vários núcleos'],

 game_gpu_level_summaries=['Frequência gráfica menor para menos calor', 'Frequência gráfica limitada a cerca de 80% para sessões longas estáveis', 'Frequência gráfica original', 'Defina você mesmo o limite de frequência gráfica'],

 installer_spoof_title='Informar a Play Store como instalador',
 installer_spoof_footer='Só os apps desta lista veem a Play Store como seu próprio instalador, então apps que exigem instalação pela Play (por exemplo Notein) continuam funcionando. Os outros apps e a Play Store continuam vendo o instalador real. Aplica-se na hora.',
 installer_spoof_remove_message='Este app voltará a ver seu instalador real.',
 app_compat_category='Compatibilidade de apps',
 device_spoof_title='Identidade do dispositivo para apps',
 device_spoof_main_switch='Mostrar outro dispositivo aos apps selecionados',
 device_spoof_device_category='Dispositivo',
 device_spoof_brand='Marca',
 device_spoof_manufacturer='Fabricante',
 device_spoof_model='Modelo',
 device_spoof_unchanged='Sem alteração',
 device_spoof_not_installed='%1$s (não instalado)',
 device_spoof_summary='%1$s · %2$s',
 device_spoof_reset='Restaurar padrão',
 device_spoof_reset_summary='OnePlus Pad Go 2 para a Netflix',
 device_spoof_reset_message='O dispositivo, os apps e a chave voltam ao padrão.',
 device_spoof_remove_message='Este app verá o dispositivo real novamente.',
 device_spoof_footer='Somente os apps desta lista veem esta marca, este fabricante e este modelo em vez do dispositivo real, por exemplo para que a Netflix transmita em HDR10, HDR10+ e Dolby Vision. O Google Play Services e a Play Store sempre veem o dispositivo real. Reinicie o app para aplicar uma alteração.',
)
L['ru'] = dict(
 app_name='Custom Tweaks',
 app_summary='Настройка игровой производительности, источник установки Play Маркет',
 game_perf_title='Производительность в играх',
 game_perf_on='Вкл.',
 game_perf_off='Выкл.',
 game_perf_main_switch='Профили производительности для приложений',
 game_mem_clean_title='Освобождать память для игр',
 game_mem_clean_summary='Закрывает фоновые приложения при запуске приложения из списка',
 game_apps_category='Приложения',
 game_app_add='Добавить приложение',
 game_app_remove='Удалить',
 game_app_levels='CPU %1$s · GPU %2$s',
 game_cpu_category='CPU',
 game_gpu_category='GPU',
 game_perf_footer='Профиль действует, только пока его приложение на экране. После выхода из приложения частоты возвращаются к норме. Защита от перегрева всегда активна.',
 game_custom_cpu_title='Частота ЦП',
 game_custom_gpu_title='Лимит GPU',
 game_custom_range_summary='30–100 %',
 game_custom_cpu_summary='Свой %1$d%%',
 game_custom_gpu_summary='Свой %1$d%%',
 game_custom_level='Свой',
 play_store_category='Play Маркет',
 game_perf_summary='Профили ЦП и ГП для каждого приложения; настройки действуют, пока приложение на экране',

 game_level_entries=['Энергосбережение', 'Баланс', 'По умолчанию'],

 game_cpu_level_summaries=['Пониженные частоты: меньше нагрев, дольше игра', 'Ограничивает главное и остальные ядра примерно до 80 % для стабильности', 'Заводские частоты; защита от перегрева остаётся активной', 'Задайте лимиты одного и нескольких ядер вручную'],

 game_gpu_level_summaries=['Пониженная частота графики: меньше нагрев', 'Ограниченная частота графики примерно до 80 % для долгой игры', 'Заводская частота графики', 'Задайте лимит частоты графики вручную'],

 installer_spoof_title='Указывать Play Маркет как установщик',
 installer_spoof_footer='Только приложения из этого списка видят Play Маркет как свой установщик, поэтому приложения, требующие установки из Play (например Notein), продолжают работать. Другие приложения и Play Маркет по-прежнему видят настоящий установщик. Применяется сразу.',
 installer_spoof_remove_message='Это приложение снова будет видеть настоящий установщик.',
 app_compat_category='Совместимость приложений',
 device_spoof_title='Данные устройства для приложений',
 device_spoof_main_switch='Показывать выбранным приложениям другое устройство',
 device_spoof_device_category='Устройство',
 device_spoof_brand='Бренд',
 device_spoof_manufacturer='Производитель',
 device_spoof_model='Модель',
 device_spoof_unchanged='Не изменяется',
 device_spoof_not_installed='%1$s (не установлено)',
 device_spoof_summary='%1$s · %2$s',
 device_spoof_reset='Сбросить настройки',
 device_spoof_reset_summary='OnePlus Pad Go 2 для Netflix',
 device_spoof_reset_message='Устройство, список приложений и переключатель вернутся к значениям по умолчанию.',
 device_spoof_remove_message='Это приложение снова будет видеть настоящее устройство.',
 device_spoof_footer='Только приложения из этого списка видят этот бренд, производителя и модель вместо настоящего устройства, например чтобы Netflix показывал HDR10, HDR10+ и Dolby Vision. Сервисы Google Play и Play Маркет всегда видят настоящее устройство. Чтобы применить изменение, перезапустите приложение.',
)


def esc(s):
    s = s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    s = s.replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"')
    if s.startswith('@') or s.startswith('?'):
        s = '\\' + s
    return s


def attrs(s):
    # a literal % without positional args would be taken as a format string
    literal = re.sub(r'%\\d\\$[sd]', '', s)
    return ' formatted="false"' if '%' in literal else ''


HEADER = '''<?xml version="1.0" encoding="utf-8"?>
<!--
     SPDX-FileCopyrightText: 2026 The LineageOS Project
     SPDX-License-Identifier: Apache-2.0
-->
<!-- Generated by tools/custom_strings.py; edit the table there. -->
<resources>
'''

en = L['en']
for lang, table in L.items():
    # Keys a translation does not carry yet fall back to English; English must
    # be complete.
    missing = [k for k, _ in KEYS if k not in table] + [a for a in ARRAYS if a not in table]
    assert not missing or lang != 'en', (lang, missing)
    out = [HEADER]
    for key, _ in KEYS:
        v = table.get(key, en[key])
        if lang != 'en' and v == en[key] and key not in ('app_name',):
            continue
        out.append(f'    <string name="{key}"{attrs(v)}>{esc(v)}</string>\n')
    for a in ARRAYS:
        items = table.get(a, en[a])
        assert len(items) == len(en[a]), (lang, a)
        out.append(f'    <string-array name="{a}">\n')
        for it in items:
            assert it.count('%') <= 1, it  # arrays are not formatted
            out.append(f'        <item>{esc(it)}</item>\n')
        out.append('    </string-array>\n')
    out.append('</resources>\n')
    d = os.path.join(RES, 'values' if lang == 'en' else 'values-' + lang)
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, 'strings.xml'), 'w') as f:
        f.write(''.join(out))
    print(lang, d)
