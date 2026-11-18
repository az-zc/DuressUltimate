package duress.ultimate;

import android.text.util.Linkify;
import android.text.method.LinkMovementMethod;
import android.app.PendingIntent;
import android.os.UserManager;
import java.io.File;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import java.io.OutputStream;
import android.app.ActivityManager;
import android.app.admin.DeviceAdminInfo;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.provider.Settings;
import android.util.Base64;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

public class MainActivity extends Activity {

	private static final String PREFS = "prefs";
	private static final String CE_PREFS = "ce_prefs";
	private static final String READ_INTRO = "read_intro";
	private static final String DURESS_LEN = "duress_len";
	private static final String MAX_ATTEMPTS = "max_attempts";
	private static final String APP_PIN_HASH = "app_pin_hash";
	private static final String APP_PIN_SALT = "app_pin_salt";
	private static final String CLOSE_WARNINGS = "close_warnings";

	private AlertDialog hideAppsDialog;
	private AlertDialog hideAppsErrorDialog;

	private TextView text;
	private AlertDialog dialog;
	private AlertDialog deviceOwnerDialog;
	private LinearLayout buttonBox;
	private TextView customInputDisplay;
	private StringBuilder currentInput = new StringBuilder();
	private boolean dialogShown = false;
	private boolean isPinAuthenticated = false;

	private BroadcastReceiver screenOffReceiver;

	private AlertDialog usbWarningDialog;

	private static boolean WasRead=false;

	private static final String SECRET_CODE_HASH = "secret_code_hash";

	private static final String SECRET_CODE_SALT = "secret_code_salt";

	private AlertDialog hideLauncherDialog;
			
	private void mask() throws java.lang.Exception {
	android.content.pm.PackageManager pm = getPackageManager();

	pm.setComponentEnabledSetting(
		new android.content.ComponentName(getPackageName(), "duress.ultimate.ProxyAlias"),
		android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
		android.content.pm.PackageManager.DONT_KILL_APP
	);

	pm.setComponentEnabledSetting(
		new android.content.ComponentName(getPackageName(), "duress.ultimate.ProxyAlias2"),
		android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
		android.content.pm.PackageManager.DONT_KILL_APP
	);
	}
	
	private String generateRandom5DigitCode() {
	SecureRandom random = new SecureRandom();
	int code = 10000 + random.nextInt(90000);
	return String.valueOf(code);

	}
	
	private void showHideLauncherAlert() {
	if (hideLauncherDialog != null && hideLauncherDialog.isShowing()) return;

	final String generatedCode = generateRandom5DigitCode();

	String title = isEn() ? "伪装应用" : "Замаскировать приложение";
	String message = isEn() 
	? "是否在桌面上伪装应用？它将会伪装成计算器。要进入应用，你需要输入 " + generatedCode + "="
	: "Замаскировать приложение на рабочем столе? Оно будет косить под калькулятор. Чтобы войти вам нужно будет ввести " + generatedCode + "=";

	hideLauncherDialog = new AlertDialog.Builder(MainActivity.this)
			.setTitle(title)
			.setMessage(message)
			.setNegativeButton(isEn() ? "取消" : "Отмена", (dialog, which) -> hideLauncherDialog = null)
			.setPositiveButton(isEn() ? "确定" : "OK", (dialog, which) -> {
				try {					
					byte[] saltBytes = new byte[16];
					new SecureRandom().nextBytes(saltBytes);
					String saltBase64 = Base64.encodeToString(saltBytes, Base64.NO_WRAP);
										
					String codeHash = hashPin(generatedCode, saltBase64);
					
					SharedPreferences protectedPrefs = getProtectedPrefs();
					CryptoManager.putString(protectedPrefs, CryptoManager.DE_ALIAS, SECRET_CODE_SALT, saltBase64);
					CryptoManager.putString(protectedPrefs, CryptoManager.DE_ALIAS, SECRET_CODE_HASH, codeHash);
										
					mask();

					finishAndRemoveTask();
					
				} catch (Throwable e) {
					Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
				}
			})
			.create();

	hideLauncherDialog.setOnDismissListener(dialog -> hideLauncherDialog = null);
	hideLauncherDialog.show();

	Window window = hideLauncherDialog.getWindow();
	if (window != null) {
		WindowManager.LayoutParams layoutParams = window.getAttributes();
		layoutParams.gravity = Gravity.CENTER;
		layoutParams.x = 0;
		layoutParams.y = 0;
		window.setAttributes(layoutParams);
	}

	TextView messageView = hideLauncherDialog.findViewById(android.R.id.message);
	if (messageView != null) {
		messageView.setTextIsSelectable(true);
	}

	}

	private void showUsbWarningAlert() {
		showUsbWarningAlert(true);
	}
		
	private void showUsbWarningAlert(boolean i) {
	if (usbWarningDialog != null && usbWarningDialog.isShowing()) return;	

	String alertTitle=null;
	String alertMsg=null;

	if (i) {	
		
	if (new java.io.File("/product/app/VanadiumConfig").exists()) {
	alertTitle = isEn() ? "提示" : "Советы";
	alertMsg = isEn() 
		? "如果你使用的是 GrapheneOS，那么在设备的 exploit protection 设置中禁用 USB 端口会有效得多。"
		: "Если у вас GrapheneOS, то отключать USB порт гораздо эффективнее в настройках exploit protection на вашем устройстве.";
	} else {
		alertTitle = isEn() ? "警告：" : "Предупреждение:";
		alertMsg = isEn() 
		? "禁用 USB 功能是在操作系统层面进行的，并不会影响 USB 端口的底层逻辑，因此它不能提供 100% 的保护。\n这只是迈向安全的一步。\n如果你希望能够完全禁用 USB 端口，最好使用 GrapheneOS 操作系统。"
		: "Отключение USB функций происходит на уровне операционной системы и не затрагивает низкоуровневую логику USB порта, тоесть не даёт 100-процентной защиты.\nЭто лишь шаг к безопасности.\nЕсли вы хотите возможность полного отключения USB порта, лучше использовать операционную систему GrapheneOS.";
	}} else {
		if (WasRead) return;
		WasRead=true;
		alertTitle = isEn() ? "警告：" : "Предупреждение:";
		alertMsg = isEn() 
		? "在开始设置之前，请确保你的屏幕锁类型确实是密码，而不是 PIN 码、图案或其他方式。否则任何错误都会导致数据重置。\n\n同时请确保你已在手机安全设置中禁用生物识别解锁和信任代理，因为它们可能会妨碍应用提供保护。"
		: "Перед началом настройки, убедитесь что ваш тип блокировки экрана это именно пароль, а не пин-код, графический ключ или что-либо ещё. В ином случае любая ошибка приведёт к сбросу данных.\n\nТакже убедитесь что у вас отключены разблокировка по биометрии и агенты доверия в настройках безопасности вашего телефона, ведь они могут мешать приложению осуществлять защиту.";
	}

	usbWarningDialog = new AlertDialog.Builder(this)
			.setTitle(alertTitle)
			.setMessage(alertMsg)
			.setCancelable(false)
			.setPositiveButton(isEn() ? "确定" : "OK", (dialog, which) -> usbWarningDialog = null)
			.create();

	Window window = usbWarningDialog.getWindow();
	if (window != null) {
		WindowManager.LayoutParams params = window.getAttributes();
		params.gravity = Gravity.CENTER;
		params.x = 0;
		params.y = 0;
		window.setAttributes(params);
	}

	usbWarningDialog.setOnShowListener(dialog -> {
		TextView titleView = usbWarningDialog.findViewById(getResources().getIdentifier("alertTitle", "id", "android"));
		if (titleView != null) {
			titleView.setTextColor(Color.parseColor("#ff5555"));
		}
		TextView msgView = usbWarningDialog.findViewById(android.R.id.message);
		if (msgView != null) {
			msgView.setTextIsSelectable(true);
		}
	});

	usbWarningDialog.setOnDismissListener(dialog -> usbWarningDialog = null);
	usbWarningDialog.show();
	}


	private void registerScreenOffReceiver() {
	screenOffReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
				EntryActivity.isLogged = false;
				isPinAuthenticated = false;
				finishAndRemoveTask();
			}
		}
	};
	
	IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
	if (Build.VERSION.SDK_INT >= 33) {
		registerReceiver(screenOffReceiver, filter, RECEIVER_NOT_EXPORTED);
	} else {
		registerReceiver(screenOffReceiver, filter);
	} }

	private void unregisterScreenOffReceiver() {
	if (screenOffReceiver != null) {
		unregisterReceiver(screenOffReceiver);
		screenOffReceiver = null;
	} }
	   
	private void EnableComponent() {
		if (isComponentEnabled()) return;
		ComponentName componentName = new ComponentName(this, MainActivity.class);

		PackageManager packageManager = getPackageManager();
		packageManager.setComponentEnabledSetting(
				componentName,
				PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
				PackageManager.DONT_KILL_APP
		);
	}	

	private boolean isComponentEnabled() {
		ComponentName componentName = new ComponentName(this, MainActivity.class);
		PackageManager pm = getPackageManager();
		return pm.getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
	}
	
	private boolean isEn() { return !Locale.getDefault().getLanguage().equals("ru"); }

	@Override
	protected void onCreate(Bundle b) {		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);	   
		super.onCreate(b);
		isPinAuthenticated = false;
		if (!EntryActivity.isLogged) {
			finishAndRemoveTask();
			return;
		}
		UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
		KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
		if (um == null || km == null || !um.isUserUnlocked() || km.isKeyguardLocked()) {
			finishAndRemoveTask();
			return;
		}
		
		registerScreenOffReceiver();
		CryptoManager.initKeys();		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		ScrollView scrollView = new ScrollView(this);
		scrollView.setFillViewport(true);
		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setGravity(Gravity.CENTER);
		root.setPadding(64, 64, 64, 64);
		
		text = new TextView(this);
		text.setGravity(Gravity.CENTER_HORIZONTAL);
		text.setTextSize(16f);
		text.setTextColor(Color.WHITE);

		buttonBox = new LinearLayout(this);
		buttonBox.setOrientation(LinearLayout.VERTICAL);
		buttonBox.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		boxParams.setMargins(0, 64, 0, 0);
		buttonBox.setLayoutParams(boxParams);

		root.addView(text);
		root.addView(buttonBox);
		scrollView.addView(root);
		setContentView(scrollView);
		
		SharedPreferences ce = getCEPrefs();
		if (ce.contains(APP_PIN_HASH)) {
			isPinAuthenticated = false;
			render(isEn() ? "请输入 PIN 码以访问应用" : "Введите пин-код для доступа к приложению");
			renderPinInputStep(isEn() ? "验证 PIN" : "Auth PIN", 8, Integer.MAX_VALUE);
		} else {
			isPinAuthenticated = true;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (!EntryActivity.isLogged) {
			finishAndRemoveTask();
			return;
		}
		UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
		KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
		if (um == null || km == null || !um.isUserUnlocked() || km.isKeyguardLocked()) {
			finishAndRemoveTask();
			return;
		}
		hideSystemUI();
		if (isPinAuthenticated) {
			updateUI();
		}
	}

	private SharedPreferences getProtectedPrefs() {
		return getApplicationContext().createDeviceProtectedStorageContext().getSharedPreferences(PREFS, MODE_PRIVATE);
	}

	private SharedPreferences getCEPrefs() {
		return getApplicationContext().getSharedPreferences(CE_PREFS, MODE_PRIVATE);
	}
	
	private boolean isDeviceOwner() {
		DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
		return dpm != null && dpm.isDeviceOwnerApp(getPackageName());
	}

	private void showDeviceOwnerInstruction() {
		String msg = isEn() ? "这些功能仅在拥有 Device Owner 权限时可用。要获得该权限，设备上不能有账户或第三方用户。如果存在，请删除它们或直接恢复出厂设置。\n然后重新安装此应用，并使用 adb 命令激活 Device Owner：\nadb shell dpm set-device-owner duress.ultimate/.MyDeviceAdminReceiver" 
							: "Эти функции доступны только если есть права Device Owner, для того чтобы их получить у вас не должно быть аккунтов и сторонних пользователей на устройстве. Если они есть, удалите их или просто сбросьте настройки.\nЗатем установите снова это приложение и используйте adb комманду для активации Device Owner:\nadb shell dpm set-device-owner duress.ultimate/.MyDeviceAdminReceiver";	  
		
		deviceOwnerDialog = new AlertDialog.Builder(this)
				.setMessage(msg)
				.setPositiveButton(isEn() ? "确定" : "OK", (dialog, which) -> deviceOwnerDialog = null)
				.create();

		Window window = deviceOwnerDialog.getWindow();
		if (window != null) {
			WindowManager.LayoutParams params = window.getAttributes();
			params.gravity = Gravity.CENTER;
			params.x = 0;
			params.y = 0;
			window.setAttributes(params);
		}

		deviceOwnerDialog.setOnDismissListener(dialog -> deviceOwnerDialog = null);
		deviceOwnerDialog.show();

		TextView messageView = deviceOwnerDialog.findViewById(android.R.id.message);
		if (messageView != null) {
			messageView.setTextIsSelectable(true);
		}
	}

	private void updateUI() {
		SharedPreferences p = getProtectedPrefs();
		SharedPreferences ce = getCEPrefs();

		boolean readIntro = CryptoManager.getBoolean(p, CryptoManager.BFU_ALIAS, READ_INTRO, false);
		boolean admin = isAdmin();
		boolean accessibility = isAccessibilityEnabled();
		boolean hasDuressLen = p.contains(DURESS_LEN);
		boolean hasMaxAttempts = p.contains(MAX_ATTEMPTS);
		boolean hasPin = ce.contains(APP_PIN_HASH);

		if (!hasPin && isComponentEnabled()) {
			android.view.ViewGroup content = findViewById(android.R.id.content);
			ScrollView scrollView = (ScrollView) content.getChildAt(0);
			LinearLayout root = (LinearLayout) scrollView.getChildAt(0);
			root.setBackgroundColor(Color.parseColor("#7a1c1c"));
			text.setTextSize(24f);
			render(isEn() ? TEXT_ERROR_EN : TEXT_ERROR);
			return;
		}
		
		if (!readIntro) {
			text.setText(isEn() ? TEXT_INTRO_EN : TEXT_INTRO);
			Linkify.addLinks(text, Linkify.WEB_URLS);
			text.setMovementMethod(LinkMovementMethod.getInstance());
			renderButtons(isEn() ? new String[]{"继续"} : new String[]{"Продолжить"}, null, false);
			return;
		}

		if (!hasDuressLen) {
			showUsbWarningAlert(false);				
			render(isEn() ? TEXT_DURESS_LEN_EN : TEXT_DURESS_LEN);
			renderInputStep(isEn() ? "保存" : "Сохранить", 4, Integer.MAX_VALUE);
			return;
		}
		
		if (!hasMaxAttempts) {
			render(isEn() ? TEXT_MAX_ATTEMPTS_EN : TEXT_MAX_ATTEMPTS);
			renderInputStep(isEn() ? "保存" : "Сохранить", 1, 5);
			return;
		}	  
				
		if (!admin) {			
			render(isEn() ? TEXT_ADMIN_EN : TEXT_ADMIN);
			renderButtons(isEn() ? new String[]{"授予权限"} : new String[]{"Дать права"}, null, false);
			return;
		}
		if (!accessibility) {  			
			if (dialogShown) {
				render(isEn() ? TEXT_RESTRICTED_EN : TEXT_RESTRICTED);
				renderButtons(isEn() ? new String[]{"应用设置", "无障碍设置"} : new String[]{"Настройки приложения", "Настройки спецвозможностей"}, null, false);
			} else {
				render(isEn() ? TEXT_ACCESSIBILITY_EN : TEXT_ACCESSIBILITY);
				renderButtons(isEn() ? new String[]{"启用无障碍"} : new String[]{"Включить спецвозможности"}, null, false);
			}
			return;
		}		
		showAccessibilityCrashAlert();
		if (!hasPin) {
			render(isEn() ? TEXT_SET_PIN_EN : TEXT_SET_PIN);
			renderPinInputStep(isEn() ? "保存 PIN" : "Сохранить ПИН", 8, Integer.MAX_VALUE);
			return;
		}

		int duressLen = CryptoManager.getInt(p, CryptoManager.BFU_ALIAS, DURESS_LEN, 4);
		int maxAttempts = CryptoManager.getInt(p, CryptoManager.BFU_ALIAS, MAX_ATTEMPTS, 3);

		String infoText = isEn()
				? "输入并提交该长度的屏幕锁密码后，手机数据将被清除：" + duressLen + "\n\n屏幕解锁密码输入失败达到该次数后，手机数据将被清除：" + maxAttempts
				: "Длина пароля разблокировки экрана после ввода и отправки которой происходит сброс данных телефона: " + duressLen + "\n\nМаксимальное количество неверных попыток подбора пароля разблокировки экрана для сброса данных телефона: " + maxAttempts;

		render(infoText);
		renderMainSettingsMenu(isEn()
				? new String[]{"修改用于重置的屏幕锁密码长度", "修改用于重置的屏幕锁密码尝试次数", "修改应用 PIN"}
				: new String[]{"Изменить длину пароля блокировки экрана для сброса", "Изменить количество попыток подбора пароля блокировки экрана для сброса", "Изменить пин-код приложения"});
	}

	private void render(String textValue) { text.setText(textValue); }

	private void renderMainSettingsMenu(String[] actions) {
		buttonBox.removeAllViews();

		SharedPreferences p = getProtectedPrefs();
		boolean isCloseWarningsEnabled = CryptoManager.getBoolean(p, CryptoManager.BFU_ALIAS, CLOSE_WARNINGS, true);
		
		DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
		ComponentName adminName = new ComponentName(this, MyDeviceAdminReceiver.class);
		boolean isDO = isDeviceOwner();

		CheckBox checkBox = new CheckBox(this);
		checkBox.setText(isEn() ? TEXT_TOGGLE_CLOSE_WARNINGS_EN : TEXT_TOGGLE_CLOSE_WARNINGS);
		checkBox.setTextColor(Color.WHITE);
		checkBox.setTextSize(16f);
		checkBox.setChecked(isCloseWarningsEnabled);				
		checkBox.setOnClickListener(v -> {
			if (!checkBox.isChecked()) {
				checkBox.setChecked(true);
				render(isEn() ? TEXT_CONFIRM_DISABLE_WARNINGS_EN : TEXT_CONFIRM_DISABLE_WARNINGS);
				renderButtons(isEn()
						? new String[]{"是，禁用关闭弹窗。", "否，保留关闭弹窗。"}
						: new String[]{"Да, отключить закрытие всплывающих окон.", "Нет, оставить закрытие всплывающих окон."}, null, false);
			} else {
				CryptoManager.putBoolean(p, CryptoManager.BFU_ALIAS, CLOSE_WARNINGS, true);
				Toast.makeText(MainActivity.this, isEn() ? TOAST_ENABLED_EN : TOAST_ENABLED, Toast.LENGTH_SHORT).show();
			}
		});
		buttonBox.addView(checkBox);
		
		if (Build.VERSION.SDK_INT >= 31) {
		CheckBox cbUsbAndDebug = new CheckBox(this);
		cbUsbAndDebug.setText(isEn() ? "禁止 USB 连接和调试功能" : "Запретить USB-подключения и функции отладки");
		cbUsbAndDebug.setTextColor(Color.WHITE);
		cbUsbAndDebug.setTextSize(16f);
		
		if (isDO) {
			boolean usbDataDisabled = !dpm.isUsbDataSignalingEnabled();
			Bundle restrictions = dpm.getUserRestrictions(adminName);
			boolean usbFileTransferDisabled = restrictions.getBoolean(UserManager.DISALLOW_USB_FILE_TRANSFER, false);
			boolean adbDisabled = restrictions.getBoolean(UserManager.DISALLOW_DEBUGGING_FEATURES, false);

			cbUsbAndDebug.setChecked(usbDataDisabled && usbFileTransferDisabled && adbDisabled);
		} else {
			cbUsbAndDebug.setChecked(false);
			cbUsbAndDebug.setAlpha(0.5f);
		}

		cbUsbAndDebug.setOnClickListener(v -> {
			if (!isDO) {
				cbUsbAndDebug.setChecked(false);
				showDeviceOwnerInstruction();
				return;
			}
			if (cbUsbAndDebug.isChecked()) {								
				dpm.setUsbDataSignalingEnabled(false);				
				dpm.addUserRestriction(adminName, UserManager.DISALLOW_USB_FILE_TRANSFER);
				dpm.addUserRestriction(adminName, UserManager.DISALLOW_DEBUGGING_FEATURES);
				showUsbWarningAlert();
			} else {				
				dpm.setUsbDataSignalingEnabled(true);				
				dpm.clearUserRestriction(adminName, UserManager.DISALLOW_USB_FILE_TRANSFER);
				dpm.clearUserRestriction(adminName, UserManager.DISALLOW_DEBUGGING_FEATURES);
			}
		});
		buttonBox.addView(cbUsbAndDebug);
		}

	if (isDO) {
	CheckBox cbCameraAndCapture = new CheckBox(this);
		cbCameraAndCapture.setText(isEn()
		? "禁止相机和截图"
		: "Запретить камеру и скриншоты");
		cbCameraAndCapture.setTextColor(Color.WHITE);
		cbCameraAndCapture.setTextSize(16f);

		boolean isCamDisabled = false;
		boolean isScrDisabled = false;

		if (isDO) {	
			
			isCamDisabled = dpm.getCameraDisabled(adminName);		
			isScrDisabled = dpm.getScreenCaptureDisabled(adminName);					
			cbCameraAndCapture.setChecked(isCamDisabled && isScrDisabled);

		} else {   
			cbCameraAndCapture.setChecked(false);   
			cbCameraAndCapture.setAlpha(0.5f);
		}

	cbCameraAndCapture.setOnClickListener(v -> {
	
			if (!isDO) {	 
				cbCameraAndCapture.setChecked(false);	  
				showDeviceOwnerInstruction();	   
				return;   
			}

	boolean shouldDisable = cbCameraAndCapture.isChecked();

	dpm.setCameraDisabled(adminName, shouldDisable);
	dpm.setScreenCaptureDisabled(adminName, shouldDisable);	
		
	});

	buttonBox.addView(cbCameraAndCapture);

	CheckBox cbRestrictions1 = new CheckBox(this);
	cbRestrictions1.setText(isEn() ? "禁止自动填充、备份和挂载物理介质" : "Запретить автозаполнение, бэкап и монтирование физических носителей");
	cbRestrictions1.setTextColor(Color.WHITE);
	cbRestrictions1.setTextSize(16f);
	
	if (isDO) {
		
		CheckBox cbUsbWipe = new CheckBox(this);
		cbUsbWipe.setText(isEn() ? "任何 USB 状态变化时清除数据、存储和 eSIM，例如从 PC 或第二部手机充电、连接 Type-C 耳机或 USB 键盘。通常这不影响从普通充电器充电" : "Сброс данных, хранилища и eSIM при любом изменении состояния USB, например при зарядке от ПК или второго телефона, подключении Type-C наушников или USB-клавиатуры. Обычно это не затрагивает зарядку от премитивных зарядных блоков");
		cbUsbWipe.setTextColor(Color.WHITE);
		cbUsbWipe.setTextSize(16f);
		if (isDO) { 
			cbUsbWipe.setChecked(CryptoManager.getBoolean(p, CryptoManager.BFU_ALIAS, "usb_wipe", false));
		} else {
			cbUsbWipe.setChecked(false); 
			cbUsbWipe.setAlpha(0.5f);
		}
		cbUsbWipe.setOnClickListener(v -> { 
			if (!isDO) {   
				cbUsbWipe.setChecked(false);   
				showDeviceOwnerInstruction();	
				return;
			} 
			CryptoManager.putBoolean(p, CryptoManager.BFU_ALIAS, "usb_wipe", cbUsbWipe.isChecked());
		});		
		buttonBox.addView(cbUsbWipe);

		CheckBox cbBootWipe = new CheckBox(this);
		cbBootWipe.setText(isEn() ? "重启后清除数据、存储和 eSIM" : "Сброс данных, хранилища и eSIM после перезагрузки");
		cbBootWipe.setTextColor(Color.WHITE);
		cbBootWipe.setTextSize(16f);
		if (isDO) {	 
			cbBootWipe.setChecked(CryptoManager.getBoolean(p, CryptoManager.BFU_ALIAS, "boot_wipe", false));
		} else {	
			cbBootWipe.setChecked(false); 
			cbBootWipe.setAlpha(0.5f);
		}
		cbBootWipe.setOnClickListener(v -> { 
	
			if (!isDO) {   
				cbBootWipe.setChecked(false);   
				showDeviceOwnerInstruction();	
				return;
			} 
			CryptoManager.putBoolean(p, CryptoManager.BFU_ALIAS, "boot_wipe", cbBootWipe.isChecked());
		});		
		buttonBox.addView(cbBootWipe);
		
		Bundle restrictions = dpm.getUserRestrictions(adminName);
		boolean autofillDisabled = restrictions.getBoolean(UserManager.DISALLOW_AUTOFILL, false);
		boolean mountMediaDisabled = restrictions.getBoolean(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, false);
		boolean backupEnabled = dpm.isBackupServiceEnabled(adminName);

		cbRestrictions1.setChecked(autofillDisabled && mountMediaDisabled && !backupEnabled);
	} else {
		cbRestrictions1.setChecked(false);
		cbRestrictions1.setAlpha(0.5f);
	}

	cbRestrictions1.setOnClickListener(v -> {
		if (!isDO) {
			cbRestrictions1.setChecked(false);
			showDeviceOwnerInstruction();
			return;
		}
		if (cbRestrictions1.isChecked()) {
			dpm.addUserRestriction(adminName, UserManager.DISALLOW_AUTOFILL);
			dpm.addUserRestriction(adminName, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA);
			dpm.setBackupServiceEnabled(adminName, false);
		} else {
			dpm.clearUserRestriction(adminName, UserManager.DISALLOW_AUTOFILL);
			dpm.clearUserRestriction(adminName, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA);
			dpm.setBackupServiceEnabled(adminName, true);
		}
	});
	buttonBox.addView(cbRestrictions1);
	}

	boolean isGrantedKeyguard = dpm != null && dpm.hasGrantedPolicy(new ComponentName(this, MyDeviceAdminReceiver.class), DeviceAdminInfo.USES_POLICY_DISABLE_KEYGUARD_FEATURES);

	if (isDO && isGrantedKeyguard) {
	CheckBox cbRestrictions2 = new CheckBox(this);
	cbRestrictions2.setText(isEn() ? "禁止信任代理、生物识别解锁和锁屏通知" : "Запретить агентов доверия, разблокировку по биометрии и уведомления на экране блокировки");
	cbRestrictions2.setTextColor(Color.WHITE);
	cbRestrictions2.setTextSize(16f);
	
	if (isDO) {
		int disabledFeatures = dpm.getKeyguardDisabledFeatures(adminName);
		boolean trustAgentsDisabled = (disabledFeatures & DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS) != 0;
		boolean biometricsDisabled = (disabledFeatures & DevicePolicyManager.KEYGUARD_DISABLE_BIOMETRICS) != 0;
		boolean secureNotificationsDisabled = (disabledFeatures & DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS) != 0;

		cbRestrictions2.setChecked(trustAgentsDisabled && biometricsDisabled && secureNotificationsDisabled);
	} else {
		cbRestrictions2.setChecked(false);
		cbRestrictions2.setAlpha(0.5f);
	}

	cbRestrictions2.setOnClickListener(v -> {
		if (!isDO) {
			cbRestrictions2.setChecked(false);
			showDeviceOwnerInstruction();
			return;
		}
		int currentFeatures = dpm.getKeyguardDisabledFeatures(adminName);
		if (cbRestrictions2.isChecked()) {
			int newFeatures = currentFeatures | DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS 
			| DevicePolicyManager.KEYGUARD_DISABLE_BIOMETRICS					
			| DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS;
			dpm.setKeyguardDisabledFeatures(adminName, newFeatures);
		} else {
			int newFeatures = currentFeatures & ~DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS 
			& ~DevicePolicyManager.KEYGUARD_DISABLE_BIOMETRICS
			& ~DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS;
			dpm.setKeyguardDisabledFeatures(adminName, newFeatures);
		}
	});
	buttonBox.addView(cbRestrictions2);
	}		
		
		CheckBox cbReboot = new CheckBox(this);
		cbReboot.setText(isEn() ? "自动重启（息屏 30 分钟后）" : "Авто-перезагрузка (30 мин после выкл экрана)");
		cbReboot.setTextColor(Color.WHITE);
		cbReboot.setTextSize(16f);
		if (isDO) { 
			cbReboot.setChecked(CryptoManager.getBoolean(p, CryptoManager.BFU_ALIAS, "auto_reboot", false));
		} else {
			cbReboot.setChecked(false); 
			cbReboot.setAlpha(0.5f);
		}
		cbReboot.setOnClickListener(v -> { 
			if (!isDO) {   
				cbReboot.setChecked(false);   
				showDeviceOwnerInstruction();	
				return;
			} 
			CryptoManager.putBoolean(p, CryptoManager.BFU_ALIAS, "auto_reboot", cbReboot.isChecked());
		});		
		buttonBox.addView(cbReboot);

		if (isDO) {

			CheckBox cbSafeBoot = new CheckBox(this);
			cbSafeBoot.setText(isEn() ? "禁止安全模式（以增加绕过应用功能的难度）"
			: "Запретить безопасный режим (чтобы усложнить обход действия приложения)");
			cbSafeBoot.setTextColor(Color.WHITE);
			cbSafeBoot.setTextSize(16f);

			if (isDO) {	
				Bundle restrictions = dpm.getUserRestrictions(adminName);   
				boolean safeBootDisabled = restrictions.getBoolean(UserManager.DISALLOW_SAFE_BOOT, false);	
				cbSafeBoot.setChecked(safeBootDisabled);
			} else {	 
				cbSafeBoot.setChecked(false);	   
				cbSafeBoot.setAlpha(0.5f);
			}

			cbSafeBoot.setOnClickListener(v -> {	
   
				if (!isDO) {			 
					cbSafeBoot.setChecked(false);			
					showDeviceOwnerInstruction();			 
					return;	 
				}
   
				if (cbSafeBoot.isChecked()) {			 
					dpm.addUserRestriction(						   
						adminName,							
						UserManager.DISALLOW_SAFE_BOOT				
					);
	
				} else {			  
					dpm.clearUserRestriction(						
						adminName,						
						UserManager.DISALLOW_SAFE_BOOT			  
					);	  
				}
			});

			buttonBox.addView(cbSafeBoot);

			CheckBox cbUserSwitch = new CheckBox(this);
			cbUserSwitch.setText(isEn() ? "移除系统用户切换按钮（这不会影响无痕模式按钮）"
			: "Убрать системную кнопку переключения пользователей (это не повлияет на кнопку режима инкогнито)");
			cbUserSwitch.setTextColor(Color.WHITE);
			cbUserSwitch.setTextSize(16f);

		   if (isDO) {
			Bundle restrictions = dpm.getUserRestrictions(adminName);
			boolean userSwitchDisabled = restrictions.getBoolean(UserManager.DISALLOW_USER_SWITCH, false);
			cbUserSwitch.setChecked(userSwitchDisabled);
		   } else {   
			   cbUserSwitch.setChecked(false);	
			   cbUserSwitch.setAlpha(0.5f);
		   }

			cbUserSwitch.setOnClickListener(v -> {	
				if (!isDO) {	   
					cbUserSwitch.setChecked(false);	  
					showDeviceOwnerInstruction();		
					return;  
				}

   
				if (cbUserSwitch.isChecked()) {	   
					dpm.addUserRestriction(				
						adminName,				
						UserManager.DISALLOW_USER_SWITCH		
					);
   
				} else {	   
					dpm.clearUserRestriction(			  
						adminName,			  
						UserManager.DISALLOW_USER_SWITCH		
					);	
				}
			});

			LinearLayout.LayoutParams usParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);	
			usParams.setMargins(0, 0, 0, 32);	   
			cbUserSwitch.setLayoutParams(usParams);				
			buttonBox.addView(cbUserSwitch);
		
			Button incognitoButton = new Button(this);
			incognitoButton.setText(isEn() ? "无痕模式" : "Режим инкогнито");

			GradientDrawable incognitoShape = new GradientDrawable();
			incognitoShape.setShape(GradientDrawable.RECTANGLE);
			incognitoShape.setColor(Color.parseColor("#34495e"));
			incognitoShape.setCornerRadius(6f);

			incognitoButton.setBackground(incognitoShape);
			incognitoButton.setTextColor(Color.WHITE);
			incognitoButton.setPadding(32, 32, 32, 32);

			LinearLayout.LayoutParams incognitoParams =  new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT );

			incognitoParams.setMargins(0, 16, 0, 16);
			incognitoButton.setLayoutParams(incognitoParams);
			incognitoButton.setOnClickListener(v -> {
   			   
			String message = isEn()
			? "无痕模式是一种切换到空临时配置文件的功能，该配置文件会一直存在到下次重启。\n\n"
			+ "进入此模式时，应用将重启手机，然后加载该配置文件。\n\n"
			+ "切换后，某些应用功能（例如自动重启和 USB 清除）可能无法工作，因为该环境中不会有它的进程。那里会有一个独立的应用进程，拥有独立的内存和权限。\n\n"
			+ "你在那里唯一能对它做的事情就是设置尝试次数限制。"
			: "Режим инкогнито это функция переключения на пустой временный профиль который будет жить до следующей перезагрузки.\n\n"
			+ "При переходе в этот режим приложение перезагрузит телефон и затем загрузит этот профиль.\n\n"
			+ "Некоторые функции приложения например авто-перезагрузка и сброс при USB после перехода могут не работать так как там не будет его процесса. Там будет отдельный процесс приложения с отдельной памятью и правами.\n\n"
			+ "Все что вы сможете там с ним сделать - это установить лимит попыток.";
	
			incognitoDialog = new AlertDialog.Builder(MainActivity.this)
			.setTitle(isEn() ? "无痕模式" : "Режим инкогнито")
			.setMessage(message)
			.setNegativeButton(isEn() ? "取消" : "Отмена", null)
			.setPositiveButton(isEn() ? "确定" : "OK", (dialog, which) -> {				
			
				try {									
					CryptoManager.putBoolean(p, CryptoManager.BFU_ALIAS, "incognito_mode", true);				
					dpm.reboot(adminName);								
				} catch (Throwable e) { 
					CryptoManager.putBoolean(p, CryptoManager.BFU_ALIAS, "incognito_mode", false);									
					Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();		   
				}			
			
			})
			.create();

	incognitoDialog.setOnDismissListener(dialog -> incognitoDialog = null);
	incognitoDialog.show();
	if (incognitoDialog.getWindow() != null) {
	android.view.WindowManager.LayoutParams layoutParams = incognitoDialog.getWindow().getAttributes();
	layoutParams.gravity = android.view.Gravity.CENTER;
	layoutParams.x = 0;
	layoutParams.y = 0;
	incognitoDialog.getWindow().setAttributes(layoutParams);

	}

	});
		
	buttonBox.addView(incognitoButton);
	
	}

		Context deContext = getApplicationContext().createDeviceProtectedStorageContext();
		SharedPreferences dePrefs = deContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

		String savedHash = CryptoManager.getString(dePrefs, CryptoManager.DE_ALIAS, SECRET_CODE_HASH, null);
		String savedSalt = CryptoManager.getString(dePrefs, CryptoManager.DE_ALIAS, SECRET_CODE_SALT, null);

		if (savedHash == null && savedSalt == null) {		   

		Button hideButton = new Button(this);
		hideButton.setText(isEn() ? "应用伪装" : "Маскировка приложения");

		GradientDrawable hideShape = new GradientDrawable();
		hideShape.setShape(GradientDrawable.RECTANGLE);
		hideShape.setColor(Color.parseColor("#34495e"));
		hideShape.setCornerRadius(6f);

		hideButton.setBackground(hideShape);
		hideButton.setTextColor(Color.WHITE);

		hideButton.setPadding(32, 32, 32, 32);


		LinearLayout.LayoutParams hideParams = new LinearLayout.LayoutParams(
		LinearLayout.LayoutParams.MATCH_PARENT,
		LinearLayout.LayoutParams.WRAP_CONTENT

		);

		hideParams.setMargins(0, 16, 0, 16);
		hideButton.setLayoutParams(hideParams);

		hideButton.setOnClickListener(v -> showHideLauncherAlert());

		buttonBox.addView(hideButton);
		}

		if (isDO) {  
			Button hideAppsButton = new Button(this);   
			hideAppsButton.setText(isEn() ? "隐藏应用" : "Скрыть приложения");
  
			GradientDrawable hideAppsShape = new GradientDrawable();	
			hideAppsShape.setShape(GradientDrawable.RECTANGLE);	
			hideAppsShape.setColor(Color.parseColor("#34495e"));   
			hideAppsShape.setCornerRadius(6f);
	
			hideAppsButton.setBackground(hideAppsShape);	
			hideAppsButton.setTextColor(Color.WHITE);   
			hideAppsButton.setPadding(32, 32, 32, 32);
   
			LinearLayout.LayoutParams hideAppsParams = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT	
			);
	
			hideAppsParams.setMargins(0, 16, 0, 16);	
			hideAppsButton.setLayoutParams(hideAppsParams);
	
			hideAppsButton.setOnClickListener(v -> showHideAppsAlert());	
			buttonBox.addView(hideAppsButton);
			
		}

					
		for (String a : actions) {
			Button b = new Button(this);
			b.setText(a);
			GradientDrawable shape = new GradientDrawable();
			shape.setShape(GradientDrawable.RECTANGLE);
			shape.setColor(Color.parseColor("#34495e"));
			shape.setCornerRadius(6f);
			b.setBackground(shape);
			b.setTextColor(Color.WHITE);
			b.setPadding(32, 32, 32, 32);
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			params.setMargins(0, 16, 0, 16);
			b.setLayoutParams(params);
			b.setOnClickListener(v -> handleAction(a));
			buttonBox.addView(b);
		}
	}

	

	private AlertDialog incognitoDialog;

	private void renderButtons(String[] actions, Button[] outButtonRef, boolean initialDisabled) {
		buttonBox.removeAllViews();
		for (int i = 0; i < actions.length; i++) {
			String a = actions[i];
			Button b = new Button(this);
			b.setText(a);
			GradientDrawable shape = new GradientDrawable();
			shape.setShape(GradientDrawable.RECTANGLE);
			shape.setColor(initialDisabled ? Color.parseColor("#4a6278") : Color.parseColor("#34495e"));
			shape.setCornerRadius(6f);
			b.setBackground(shape);
			b.setTextColor(Color.WHITE);
			b.setPadding(32, 32, 32, 32);
			b.setEnabled(!initialDisabled);
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			params.setMargins(0, 16, 0, 16);
			b.setLayoutParams(params);
			b.setOnClickListener(v -> handleAction(a));
			buttonBox.addView(b);

			if (outButtonRef != null && i == 0) {
				outButtonRef[0] = b;
			}

			if (actions.length == 1 && (a.equals("Включить спецвозможности") || a.equals("启用无障碍"))) {
				TextView hint = new TextView(this);
				hint.setText(isEn() ? "授予权限后，或如果出现问题，请使用返回按钮或手势返回应用。" : "После выдачи разрешений или в случае возникновения проблем, вернитесь в приложение используя кнопку или жест 'назад'.");
				hint.setTextColor(Color.WHITE);
				hint.setTextSize(16f);
				hint.setGravity(Gravity.CENTER);
				hint.setPadding(0, 32, 0, 0);
				buttonBox.addView(hint);
			}
		}
	}

	private void renderInputStep(String actionName, int minVal, int maxVal) {
		buttonBox.removeAllViews();
		currentInput.setLength(0);

		customInputDisplay = new TextView(this);
		customInputDisplay.setGravity(Gravity.CENTER);
		customInputDisplay.setTextSize(22f);
		customInputDisplay.setTextColor(Color.WHITE);
		customInputDisplay.setText("");
		customInputDisplay.setPadding(32, 32, 32, 32);

		GradientDrawable bgShape = new GradientDrawable();
		bgShape.setShape(GradientDrawable.RECTANGLE);
		bgShape.setColor(Color.parseColor("#2c3e50"));
		bgShape.setCornerRadius(8f);
		bgShape.setStroke(2, Color.parseColor("#7f8c8d"));
		customInputDisplay.setBackground(bgShape);

		LinearLayout.LayoutParams displayParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		displayParams.setMargins(0, 0, 0, 16);
		customInputDisplay.setLayoutParams(displayParams);
		buttonBox.addView(customInputDisplay);

		LinearLayout keypadBox = new LinearLayout(this);
		keypadBox.setOrientation(LinearLayout.VERTICAL);
		keypadBox.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams keypadParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		keypadParams.setMargins(0, 16, 0, 16);
		keypadBox.setLayoutParams(keypadParams);

		final Button[] okBtnRef = new Button[1];

		String[][] keys = {
				{"1", "2", "3"},
				{"4", "5", "6"},
				{"7", "8", "9"},
				{"⌫", "0", "OK"}
		};

		for (String[] rowKeys : keys) {
			LinearLayout rowLayout = new LinearLayout(this);
			rowLayout.setOrientation(LinearLayout.HORIZONTAL);
			rowLayout.setGravity(Gravity.CENTER);
			LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			rowParams.setMargins(0, 4, 0, 4);
			rowLayout.setLayoutParams(rowParams);

			for (String key : rowKeys) {
				Button keyBtn = new Button(this);
				keyBtn.setText(key);

				GradientDrawable keyShape = new GradientDrawable();
				keyShape.setShape(GradientDrawable.RECTANGLE);

				boolean isOk = key.equals("OK");
				if (isOk) {
					keyBtn.setEnabled(false);
					keyShape.setColor(Color.parseColor("#4a6278"));
					okBtnRef[0] = keyBtn;
				} else {
					keyShape.setColor(Color.parseColor("#34495e"));
				}

				keyShape.setCornerRadius(6f);
				keyBtn.setBackground(keyShape);
				keyBtn.setTextColor(Color.WHITE);
				keyBtn.setTextSize(20f);
				keyBtn.setPadding(16, 24, 16, 24);

				LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
				keyParams.setMargins(4, 0, 4, 0);
				keyBtn.setLayoutParams(keyParams);

				keyBtn.setOnClickListener(v -> {
					if (key.equals("⌫")) {
						if (currentInput.length() > 0) {
							currentInput.deleteCharAt(currentInput.length() - 1);
						}
					} else if (isOk) {
						if (keyBtn.isEnabled()) {
							handleAction(actionName);
						}
					} else {
						currentInput.append(key);
					}

					customInputDisplay.setText(currentInput.toString());

					try {
						if (currentInput.length() == 0) {
							setButtonState(okBtnRef[0], false);
						} else {
							int val = Integer.parseInt(currentInput.toString());
							boolean valid = (val >= minVal && val <= maxVal);
							setButtonState(okBtnRef[0], valid);
						}
					} catch (NumberFormatException e) {
						setButtonState(okBtnRef[0], false);
					}
				});

				rowLayout.addView(keyBtn);
			}
			keypadBox.addView(rowLayout);
		}
		buttonBox.addView(keypadBox);
	}

	private void renderPinInputStep(String actionName, int minLen, int maxLen) {
		buttonBox.removeAllViews();
		currentInput.setLength(0);

		customInputDisplay = new TextView(this);
		customInputDisplay.setGravity(Gravity.CENTER);
		customInputDisplay.setTextSize(22f);
		customInputDisplay.setTextColor(Color.WHITE);
		customInputDisplay.setText("");
		customInputDisplay.setPadding(32, 32, 32, 32);

		GradientDrawable bgShape = new GradientDrawable();
		bgShape.setShape(GradientDrawable.RECTANGLE);
		bgShape.setColor(Color.parseColor("#2c3e50"));
		bgShape.setCornerRadius(8f);
		bgShape.setStroke(2, Color.parseColor("#7f8c8d"));
		customInputDisplay.setBackground(bgShape);

		LinearLayout.LayoutParams displayParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		displayParams.setMargins(0, 0, 0, 16);
		customInputDisplay.setLayoutParams(displayParams);
		buttonBox.addView(customInputDisplay);

		LinearLayout keypadBox = new LinearLayout(this);
		keypadBox.setOrientation(LinearLayout.VERTICAL);
		keypadBox.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams keypadParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		keypadParams.setMargins(0, 16, 0, 16);
		keypadBox.setLayoutParams(keypadParams);

		final Button[] okBtnRef = new Button[1];

		String[][] keys = {
				{"1", "2", "3"},
				{"4", "5", "6"},
				{"7", "8", "9"},
				{"⌫", "0", "OK"}
		};

		for (String[] rowKeys : keys) {
			LinearLayout rowLayout = new LinearLayout(this);
			rowLayout.setOrientation(LinearLayout.HORIZONTAL);
			rowLayout.setGravity(Gravity.CENTER);
			LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			rowParams.setMargins(0, 4, 0, 4);
			rowLayout.setLayoutParams(rowParams);

			for (String key : rowKeys) {
				Button keyBtn = new Button(this);
				keyBtn.setText(key);

				GradientDrawable keyShape = new GradientDrawable();
				keyShape.setShape(GradientDrawable.RECTANGLE);

				boolean isOk = key.equals("OK");
				if (isOk) {
					keyBtn.setEnabled(false);
					keyShape.setColor(Color.parseColor("#4a6278"));
					okBtnRef[0] = keyBtn;
				} else {
					keyShape.setColor(Color.parseColor("#34495e"));
				}

				keyShape.setCornerRadius(6f);
				keyBtn.setBackground(keyShape);
				keyBtn.setTextColor(Color.WHITE);
				keyBtn.setTextSize(20f);
				keyBtn.setPadding(16, 24, 16, 24);

				LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
				keyParams.setMargins(4, 0, 4, 0);
				keyBtn.setLayoutParams(keyParams);

				keyBtn.setOnClickListener(v -> {
					if (key.equals("⌫")) {
						if (currentInput.length() > 0) {
							currentInput.deleteCharAt(currentInput.length() - 1);
						}
					} else if (isOk) {
						if (keyBtn.isEnabled()) {
							handleAction(actionName);
						}
					} else {
						if (currentInput.length() < maxLen) {
							currentInput.append(key);
						}
					}

					StringBuilder masked = new StringBuilder();
					for(int i = 0; i < currentInput.length(); i++) masked.append("•");
					customInputDisplay.setText(masked.toString());

					int len = currentInput.length();
					setButtonState(okBtnRef[0], (len >= minLen && len <= maxLen));
				});

				rowLayout.addView(keyBtn);
			}
			keypadBox.addView(rowLayout);
		}
		buttonBox.addView(keypadBox);
	}

	private void setButtonState(Button b, boolean enabled) {
		b.setEnabled(enabled);
		GradientDrawable shape = new GradientDrawable();
		shape.setShape(GradientDrawable.RECTANGLE);
		shape.setColor(enabled ? Color.parseColor("#34495e") : Color.parseColor("#4a6278"));
		shape.setCornerRadius(6f);
		b.setBackground(shape);
	}

	private String generateSalt() {
		byte[] salt = new byte[16];
		new SecureRandom().nextBytes(salt);
		return Base64.encodeToString(salt, Base64.NO_WRAP);
	}

	private String hashPin(String pin, String salt) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(Base64.decode(salt, Base64.NO_WRAP));
			byte[] hash = md.digest(pin.getBytes(StandardCharsets.UTF_8));
			return Base64.encodeToString(hash, Base64.NO_WRAP);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void handleAction(String action) {
		SharedPreferences p = getProtectedPrefs();
		switch (action) {
			case "Продолжить": case "继续":
				CryptoManager.putBoolean(p, CryptoManager.BFU_ALIAS, READ_INTRO, true);
				updateUI(); break;
			case "Включить спецвозможности": case "启用无障碍": case "Настройки спецвозможностей": case "无障碍设置":
				dialogShown = true;
				startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
				break;
			case "Настройки приложения": case "应用设置":
				Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
				intent.setData(Uri.fromParts("package", getPackageName(), null));
				startActivity(intent);
				break;
			case "Дать права": case "授予权限":
				Intent adminIntent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
				adminIntent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(this, MyDeviceAdminReceiver.class));
				startActivity(adminIntent);
				break;
			case "Сохранить": case "保存":
				if (currentInput.length() > 0) {
					try {
						int val = Integer.parseInt(currentInput.toString());
						if (!p.contains(DURESS_LEN)) {
							CryptoManager.putInt(p, CryptoManager.BFU_ALIAS, DURESS_LEN, val);
						} else if (!p.contains(MAX_ATTEMPTS)) {
							CryptoManager.putInt(p, CryptoManager.BFU_ALIAS, MAX_ATTEMPTS, val);
						} else {
							if (text.getText().toString().contains("длину") || text.getText().toString().contains("长度")) {
								CryptoManager.putInt(p, CryptoManager.BFU_ALIAS, DURESS_LEN, val);
							} else {
								CryptoManager.putInt(p, CryptoManager.BFU_ALIAS, MAX_ATTEMPTS, val);
							}
						}
						updateUI();
					} catch (Exception ignored) {}
				}
				break;
			case "Сохранить ПИН": case "保存 PIN":
				if (currentInput.length() >= 8 && currentInput.length() <= Integer.MAX_VALUE) {					
					String pin = currentInput.toString();
					String salt = generateSalt();
					String hash = hashPin(pin, salt);					
					SharedPreferences ce = getCEPrefs();
					CryptoManager.putString(ce, CryptoManager.CE_ALIAS, APP_PIN_SALT, salt);
					CryptoManager.putString(ce, CryptoManager.CE_ALIAS, APP_PIN_HASH, hash);
					EnableComponent();
					updateUI();
				}
				break;
			case "验证 PIN": case "Auth PIN":
				String pin = currentInput.toString();				
				SharedPreferences ce = getCEPrefs();
				String salt = CryptoManager.getString(ce, CryptoManager.CE_ALIAS, APP_PIN_SALT, "");
				String hash = CryptoManager.getString(ce, CryptoManager.CE_ALIAS, APP_PIN_HASH, "");
				if (hashPin(pin, salt).equals(hash)) {
					isPinAuthenticated = true;
					updateUI();
				} else {
					currentInput.setLength(0);
					if (customInputDisplay != null) customInputDisplay.setText("");
				}
				break;
			case "Изменить длину пароля блокировки экрана для сброса": case "修改用于重置的屏幕锁密码长度":
				render(isEn() ? TEXT_DURESS_LEN_EN : TEXT_DURESS_LEN);
				renderInputStep(isEn() ? "保存" : "Сохранить", 4, Integer.MAX_VALUE);
				break;
			case "Изменить количество попыток подбора пароля блокировки экрана для сброса": case "修改用于重置的屏幕锁密码尝试次数":
				render(isEn() ? TEXT_MAX_ATTEMPTS_EN : TEXT_MAX_ATTEMPTS);
				renderInputStep(isEn() ? "保存" : "Сохранить", 1, 5);
				break;
			case "Изменить пин-код приложения": case "修改应用 PIN":
				render(isEn() ? TEXT_SET_PIN_EN : TEXT_SET_PIN);
				renderPinInputStep(isEn() ? "保存 PIN" : "Сохранить ПИН", 8, Integer.MAX_VALUE);
				break;
			case "Да, отключить закрытие всплывающих окон.": case "是，禁用关闭弹窗。":
				CryptoManager.putBoolean(p, CryptoManager.BFU_ALIAS, CLOSE_WARNINGS, false);
				updateUI();
				break;
			case "Нет, оставить закрытие всплывающих окон.": case "否，保留关闭弹窗。":
				updateUI();
				break;
		}
	}

	private void hideSystemUI() {
		getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN);
	}

	private boolean isAccessibilityEnabled() {
		String prefString = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
		if (prefString == null || prefString.isEmpty()) return false;
		ComponentName target = new ComponentName(getPackageName(), MyAccessibilityService.class.getName());
		for (String s : prefString.split(":")) {
			ComponentName cn = ComponentName.unflattenFromString(s);
			if (target.equals(cn)) return true;
		}
		return false;
	}

	private boolean isAdmin() {
		DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
		return dpm != null && dpm.isAdminActive(new ComponentName(this, MyDeviceAdminReceiver.class));
	}

	@Override
	protected void onDestroy() {
		EntryActivity.isLogged = false;
		isPinAuthenticated = false;
		unregisterScreenOffReceiver();
		
		if (deviceOwnerDialog != null && deviceOwnerDialog.isShowing()) {
			deviceOwnerDialog.dismiss();			
		}
		deviceOwnerDialog = null;
		
		if (usbWarningDialog != null && usbWarningDialog.isShowing()) {	
			usbWarningDialog.dismiss();				
		}
		usbWarningDialog = null;

		if (dialog != null && dialog.isShowing()) {
			dialog.dismiss();
		}
		dialog = null;	

		if (incognitoDialog != null) {
		incognitoDialog.dismiss();		
		}
		incognitoDialog = null;

		if (hideLauncherDialog != null) {
		hideLauncherDialog.dismiss();		
		}
		hideLauncherDialog = null;

		if (hideAppsDialog != null) {  
			hideAppsDialog.dismiss();
		}

		hideAppsDialog = null;

		if (hideAppsErrorDialog != null) {  
			hideAppsErrorDialog.dismiss();
		}

		hideAppsErrorDialog = null;
			
		super.onDestroy();		
	}	

	private boolean isServiceRunning() {
	ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
	if (manager == null) return false;

	String targetClassName = MyAccessibilityService.class.getName();

	for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
		if (targetClassName.equals(service.service.getClassName())) {
			return true; 
		}
	}
	return false; 
	}


	private void showAccessibilityCrashAlert() {
	if (isServiceRunning()) return;	
	boolean isRu = "ru".equals(Locale.getDefault().getLanguage());

	dialog = new AlertDialog.Builder(this)
		.setTitle(isRu ? "Внимание" : "注意")
		.setCancelable(false)
		.setMessage(isRu ? "Спецвозможности включены, но сервис не запущен. Возможно произошла ошибка, например система остановила его из-за нехватки оперативной памяти. Просьба отключить ползунок сервиса а потом снова включить." 
						 : "无障碍功能已启用，但服务未运行。可能发生了错误，例如系统因内存不足而停止了它。请关闭服务开关，然后再次开启。")
		.setPositiveButton(isRu ? "ОК" : "确定", (d, w) -> 
			startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
		.create();

	if (dialog.getWindow() != null) {
		android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
		lp.gravity = android.view.Gravity.CENTER;
		lp.x = 0;
		lp.y = 0;
		dialog.getWindow().setAttributes(lp);
	}

	dialog.show(); }

	private volatile boolean hasUnsafeApps = false;	
	private volatile boolean backgroundThreadFree = true;
	private volatile boolean internalThreadFree = true;	
	
	private void showHideAppsAlert() {
	if (!backgroundThreadFree || !internalThreadFree) {return;} else {Toast.makeText(this, isEn() ? "加载中..." : "Загрузка...", Toast.LENGTH_SHORT).show();}	
	if (hideAppsDialog != null) return;
	hideAppsDialog = new AlertDialog.Builder(this).create();	
	
	final DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
	final ComponentName adminName = new ComponentName(this, MyDeviceAdminReceiver.class);
	final PackageManager pm = getPackageManager();
	
	LinearLayout container = new LinearLayout(this);
	container.setOrientation(LinearLayout.VERTICAL);

	TextView messageView = new TextView(this);
	String message = isEn()
			? "选择要隐藏的应用。隐藏应用会禁用它们。隐藏某些系统应用可能会影响设备性能。隐藏后风险最高的应用以红色标出。"
			: "Выберите приложения для скрытия. Скрытие приложений отключает их. Скрытие некоторых системных приложений может повлиять на работоспособность устройства. Приложения скрытие которых наиболее опасно выделены красным.";
	messageView.setText(message);
	messageView.setTextIsSelectable(true);
	messageView.setPadding(48, 32, 48, 16);
	container.addView(messageView);

	android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
	int scrollHeight = (int) (metrics.heightPixels * 0.6f);

	ScrollView scrollView = new ScrollView(this);
	scrollView.setLayoutParams(new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, scrollHeight));

	LinearLayout listLayout = new LinearLayout(this);
	listLayout.setOrientation(LinearLayout.VERTICAL);
	listLayout.setPadding(48, 0, 48, 16);
	scrollView.addView(listLayout);
	container.addView(scrollView);

	LinearLayout unsafeLayout = new LinearLayout(this);
	unsafeLayout.setOrientation(LinearLayout.VERTICAL);

	TextView warningHeader = new TextView(this);
	String warningText = isEn()
			? "隐藏这些应用可能不安全："
			: "Эти приложения может быть небезопасно скрывать:";
	warningHeader.setText(warningText);
	warningHeader.setTypeface(null, android.graphics.Typeface.BOLD);
	warningHeader.setPadding(0, 32, 0, 16);
	warningHeader.setTextColor(android.graphics.Color.RED);
	unsafeLayout.addView(warningHeader);
	
	final java.util.List<CheckBox> checkBoxes = new java.util.ArrayList<>();
	final java.util.List<String> packages = new java.util.ArrayList<>();
	final java.util.List<Boolean> initialStates = new java.util.ArrayList<>();
	final java.util.Map<String, CharSequence> labels = new java.util.HashMap<>();

	java.util.Set<String> launchablePkgs = new java.util.HashSet<>();
	for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(
		new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
		PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.MATCH_UNINSTALLED_PACKAGES)) {	
		launchablePkgs.add(ri.activityInfo.packageName);
	}
		
	hasUnsafeApps = false;	

	backgroundThreadFree = false;	
	new Thread(() -> {	
	java.util.List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.MATCH_ALL | PackageManager.GET_META_DATA);
	java.util.Collections.sort(apps, (a, b) -> pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString()));
				
	for (android.content.pm.ApplicationInfo appInfo : apps) {
		if (appInfo.packageName.equals(getPackageName())) continue;

		boolean isPlatformSigned = pm.checkSignatures("android", appInfo.packageName) == PackageManager.SIGNATURE_MATCH;
		boolean hasNoLogo = appInfo.icon == 0;
		boolean isSettings = "com.android.settings".equals(appInfo.packageName);
		boolean isSystemUid = appInfo.uid == android.os.Process.SYSTEM_UID;
		boolean hasNoLaunchIntent = !launchablePkgs.contains(appInfo.packageName);
		boolean isSystemApp = (appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
		String defaultIme = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
		boolean isIme = defaultIme != null && defaultIme.startsWith(appInfo.packageName + "/");
		boolean isUnsafe = (isSystemApp || isPlatformSigned || isSystemUid) && (isSettings || isIme || hasNoLogo || hasNoLaunchIntent);		
		
		CharSequence label = pm.getApplicationLabel(appInfo);
		labels.put(appInfo.packageName, label);

		android.graphics.drawable.Drawable icon = pm.getApplicationIcon(appInfo);		

		boolean hidden1;
		try {
			hidden1 = dpm.isApplicationHidden(adminName, appInfo.packageName);
		} catch (Throwable e) {
			hidden1 = false;
		}

		final boolean hidden = hidden1;
					
		runOnUiThread(() -> {

		CheckBox cb = new CheckBox(this);
		cb.setText(label + " [" + appInfo.packageName + "]");
		cb.setTextSize(13f);
		
		int iconSize = (int) (24 * metrics.density);
		icon.setBounds(0, 0, iconSize, iconSize);
		cb.setCompoundDrawables(icon, null, null, null);
		cb.setCompoundDrawablePadding((int) (8 * metrics.density));
				
		cb.setChecked(hidden);

		if (isUnsafe) {
			cb.setButtonTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.RED));
			cb.setTextColor(android.graphics.Color.RED);
			unsafeLayout.addView(cb);
			hasUnsafeApps = true;
		} else {
			listLayout.addView(cb);
		}
		
		checkBoxes.add(cb);
		packages.add(appInfo.packageName);
		initialStates.add(hidden);				
		});			
	}
	runOnUiThread(() -> {	
	if (hasUnsafeApps) listLayout.addView(unsafeLayout);
					
	hideAppsDialog = new AlertDialog.Builder(this)
			.setTitle(isEn() ? "隐藏应用" : "Скрыть приложения")
			.setView(container)
			.setCancelable(false)
			.setNegativeButton(isEn() ? "取消" : "Отмена", (dialog, which) -> hideAppsDialog = null)
			.setPositiveButton(isEn() ? "确定" : "OK", (dialog, which) -> {
				java.util.List<String> failedPackages = new java.util.ArrayList<>();
				java.util.List<String> failedReasons = new java.util.ArrayList<>();

				java.util.List<Boolean> states = new java.util.ArrayList<>();
				for (CheckBox cb : checkBoxes) {
				states.add(cb.isChecked());
				}

				Toast.makeText(MainActivity.this, isEn() ? "正在隐藏..." : "Скрытие...", Toast.LENGTH_SHORT).show();
				internalThreadFree=false;
				new Thread(() -> {
				for (int i = 0; i < checkBoxes.size(); i++) {
					String pkg = packages.get(i);
					boolean desiredHidden = states.get(i);		   
					boolean initialHidden = initialStates.get(i);

					if (desiredHidden != initialHidden) {
						try {
							boolean success = dpm.setApplicationHidden(adminName, pkg, desiredHidden);
							if (!success) {
								failedPackages.add(labels.get(pkg) + " (" + pkg + ")");
								failedReasons.add(isEn() ? "未知错误" : "Неизвестная ошибка");
							}
						} catch (Throwable e) {
							failedPackages.add(labels.get(pkg) + " (" + pkg + ")");
							failedReasons.add(e.getMessage());
						}
					}
				}

				if (!failedPackages.isEmpty()) showHideAppsErrorAlert(failedPackages, failedReasons);
				internalThreadFree=true;
				}).start();
				hideAppsDialog = null;
			}).create();

	hideAppsDialog.show();

	Window window = hideAppsDialog.getWindow();
	if (window != null) {
		WindowManager.LayoutParams lp = window.getAttributes();
		lp.gravity = Gravity.CENTER;
		lp.x = 0;
		lp.y = 0;
		window.setAttributes(lp);
	} });
	backgroundThreadFree = true;	
	}).start();
	}

	private void showHideAppsErrorAlert(java.util.List<String> failedPackages, java.util.List<String> failedReasons) {
	runOnUiThread(() -> {	
	StringBuilder sb = new StringBuilder();
	sb.append(isEn() ? "无法隐藏以下应用：" : "Не удалось скрыть следующие приложения:").append("\n\n");
	for (int i = 0; i < failedPackages.size(); i++) {
		sb.append(failedPackages.get(i)).append(" — ").append(failedReasons.get(i)).append("\n");
	}

	hideAppsErrorDialog = new AlertDialog.Builder(this)
			.setTitle(isEn() ? "错误" : "Ошибка")
			.setMessage(sb.toString())
			.setCancelable(false)
			.setPositiveButton(isEn() ? "确定" : "OK", (dialog, which) -> hideAppsErrorDialog = null)
			.create();

	hideAppsErrorDialog.show();

	Window window = hideAppsErrorDialog.getWindow();
	if (window != null) {
		WindowManager.LayoutParams lp = window.getAttributes();
		lp.gravity = Gravity.CENTER;
		lp.x = 0;
		lp.y = 0;
		window.setAttributes(lp);
	}

	TextView msgView = hideAppsErrorDialog.findViewById(android.R.id.message);
	if (msgView != null) {
		msgView.setTextIsSelectable(true);
	} });
	}
   
	private static final String TEXT_INTRO = "\n\n\nПривет! Это приложение, которое сбрасывает телефон до заводких настроек и удаляет данные при вводе пароля блокировки экрана заданной длины для сброса или при превышении лимита неверных попыток разблокировки (обычно попытка неверная попытка засчитывается если введено 4 или более символов).\n\nКак это работает:\n Вы задаете длину пароля для сброса и максимальное количество неверных попыток (от 1 до 5). По умолчанию когда приложение только получило свои права (спецвозможности и админ) лимит неверных попыток держится на уровне 1. При вводе пароля обычной длины сервис спецвозможностей временно добавляет 2 попытки (вплоть до максимального лимита). При вводе длины для сброса, лимит остается равным 1 и если вы ввели неверный пароль, происходит сброс. Длина для сброса должна отличаться от длины вашего пароля. Приложение использует такую сложную тактику с выставлением лимитов чтобы минимизировать временное окно, когда защиту можно обойти. Проще говоря, при сбое в системе или случайной остановке сервиса спецвозможностей, с наибольшей вероятностью лимит будет оставаться равен 1му или 1му от текущего количества неверных попыток, оставляя защиту в силе.\n\nРекомендация: приложение поддерживает только один тип блокировки: Пароль. Перед настройкой приложения убедитесь что у вас этот тип блокировки, отключите другие типы блокировки, например графический ключ (иначе любая ошибка приведёт к сбросу данных). Также отключите разблокировку по биометрии и агентов доверия в настройках безопасности вашего телефона (ведь они могут мешать приложению осуществлять защиту).\n\nТакже важно сообщить что сброс по лимиту попыток не удаляет раздел FRP основного профиля, который хранит ID аккаунтов. Если хотите не оставлять следов от ваших Google аккаунтов, рекомендуется хранить их только в рабочих профилях, которые не могут быть завязаны на FRP. В остальных случаях будьте аккуратны и не привязывайте бекапы и важные данные к Google аккаунтам, также убедитесь что физического доступа к СИМ-карте недостаточно для получения контроля над ними, проще говоря не привязывайте Google аккаунты к номеру телефона. А ещё поставьте пин-код на сим-карту (это важно и для остальных данных вне зависимости от наличия FRP).\n\nЕсли предоставить этому приложению права Device Owner, оно отключит FRP.\n\nИсходный код:\nhttps://github.com/pofesk0/DuressUltimate\n\nСтраница на F-droid:\nhttps://f-droid.org/ru/packages/duress.ultimate\n";
	private static final String TEXT_INTRO_EN = "\n\n\n你好！这是一个应用，当输入并提交指定长度的屏幕锁密码，或超过错误解锁尝试次数限制时（通常输入 4 个或更多字符会被计为一次错误尝试），会执行恢复出厂设置并清除数据。\n\n工作原理：\n 你可以设置用于重置的密码长度，以及最大失败尝试次数（1 到 5）。默认情况下，当应用刚获得权限（无障碍和管理员）时，失败尝试限制保持为 1。输入普通长度的密码时，无障碍服务会临时增加 2 次尝试（最多到最大限制）。输入用于重置的长度时，限制保持为 1，如果你输入了错误密码，就会触发重置。用于重置的长度必须不同于你的实际密码长度。应用使用这种复杂的限制设置策略，是为了尽量减少保护可能被绕过的临时窗口。简而言之，在系统崩溃或无障碍服务意外停止时，限制最有可能仍保持为 1，或相对于当前失败次数保持为 1，从而让保护继续生效。\n\n建议：应用只支持一种锁类型：密码。在设置应用之前，请确保你使用的是这种锁类型，禁用其他锁类型，例如图案锁（否则任何错误都会导致数据重置）。同时请在设备安全设置中禁用生物识别解锁和信任代理（因为它们可能会妨碍应用提供保护）。\n\n还需要说明的是，按尝试次数限制触发的重置不会删除主配置文件中存储账户 ID 的 FRP 分区。如果你不想留下 Google 账户痕迹，建议只将它们保存在无法与 FRP 关联的工作配置文件中。其他情况下请小心，不要将备份和重要数据绑定到 Google 账户，同时确保仅物理接触 SIM 卡不足以获得对其的控制权，简单来说不要把 Google 账户绑定到手机号码。另外请为 SIM 卡设置 PIN 码（无论是否启用 FRP，这对其他数据也很重要）。\n\n如果授予此应用 Device Owner 权限，它将禁用 FRP。\n\n源代码：\nhttps://github.com/pofesk0/DuressUltimate\n\nF-droid 页面：\nhttps://f-droid.org/ru/packages/duress.ultimate\n";
	
	private static final String TEXT_ERROR = "Возникла ошибка:\nпамять приложения была очищена либо состояние пакета изменено некорректно";
	private static final String TEXT_ERROR_EN = "发生错误：\n应用数据已被清除，或包状态被错误修改";

	private static final String TEXT_ACCESSIBILITY = "Теперь, дайте приложению Спецвозможности. Они нужны для определения длины паролей в полях ввода. Перейдите в настройки Спецвозможностей -> установленные приложения -> и включите их для DuressUltimate. Только после этого будет установлен лимит попыток.";
	private static final String TEXT_ACCESSIBILITY_EN = "现在，请授予应用无障碍功能。它们用于检测输入框中的密码长度。请进入无障碍设置 -> 已安装的应用 -> 为 DuressUltimate 启用它们。只有完成此操作后，尝试次数限制才会被设置。";

	private static final String TEXT_RESTRICTED = "Вы пытались дать разрешение на спецвозможности, но у вас не получилось? Возможно это из-за того что система блокирует возможность активации таких сервисов называя это \"ограниченными настройками\".\n\nЕсли вам написали об этом при запросе разрешения то\nПерейдите в настройки приложения, нажмите на 3 точки в правом верхнем углу и разрешите их, затем заново перейдите в настройки спецвозможностей и произведите попытку активации. Если 3 точек нет, сделайте тоже самое пока они не появятся либо пока вы не активируете сервис.";
	private static final String TEXT_RESTRICTED_EN = "你尝试授予无障碍权限，但没有成功？这可能是因为系统阻止激活此类服务，并将其称为“受限设置”。\n\n如果在请求权限时提示了这一点，那么\n请进入应用设置，点击右上角的三个点并允许受限设置，然后重新进入无障碍设置并再次尝试激活。如果没有三个点，请重复相同操作，直到它们出现，或直到你成功激活服务。";

	private static final String TEXT_ADMIN = "Для начала использования этих функций сначала дайте приложению права администратора устройства для того чтобы оно могло стирать данные с телефона при вводе заданной вами длины пароля";
	private static final String TEXT_ADMIN_EN = "要开始使用这些功能，请先授予应用设备管理员权限，以便它能在你输入所设置长度的密码时清除手机数据";

	private static final String TEXT_DURESS_LEN = "Задайте длину пароля разблокировки экрана при вводе и отправке которой происходит сброс данных телефона (от 4 и более). Затем перейдите к следующему шагу.";
	private static final String TEXT_DURESS_LEN_EN = "设置屏幕解锁密码长度（4 位及以上）。输入并提交该长度的密码后，手机数据将被清除。然后进入下一步。";

	private static final String TEXT_MAX_ATTEMPTS = "Задайте максимальное количество неверных попыток подбора пароля разблокировки экрана для сброса данных телефона (от 1 до 5). Затем перейдите к следующему шагу.";
	private static final String TEXT_MAX_ATTEMPTS_EN = "设置屏幕解锁密码输入失败达到多少次后清除手机数据（1 到 5）。然后进入下一步。";

	private static final String TEXT_SET_PIN = "Теперь рекомендуется установить пин-код на приложение (от 8 символов)";
	private static final String TEXT_SET_PIN_EN = "现在建议为应用设置 PIN 码（8 个字符或更多）";

	private static final String TEXT_TOGGLE_CLOSE_WARNINGS = "Закрывать окна предупреждений об оставшихся попытках";
	private static final String TEXT_TOGGLE_CLOSE_WARNINGS_EN = "关闭关于剩余尝试次数的警告窗口";

	private static final String TEXT_CONFIRM_DISABLE_WARNINGS = "Вы хотите отключить закрытие всплывающих окон об оставшемся количестве попыток ввода пароля до сброса данных? Обычно вам не стоит этого делать - вы и так знаете сколько у вас всего попыток, ведь вы настраивали это здесь. Зато эта информация может быть интересна другим людям, поэтому лучше скрывать эти окна. Выключайте это ТОЛЬКО ЕСЛИ опция неисправна.";
	private static final String TEXT_CONFIRM_DISABLE_WARNINGS_EN = "你想禁用关闭关于数据清除前剩余密码输入尝试次数的弹窗吗？通常你不应该这样做——你已经知道总共有多少次尝试，因为这是你在这里设置的。不过这些信息可能会引起他人兴趣，所以最好隐藏这些窗口。仅在选项发生故障时才禁用。";

	private static final String BTN_YES_DISABLE = "Да, отключить закрытие всплывающих окон.";
	private static final String BTN_YES_DISABLE_EN = "是，禁用关闭弹窗。";

	private static final String BTN_NO_KEEP = "Нет, оставить закрытие всплывающих окон.";
	private static final String BTN_NO_KEEP_EN = "否，保留关闭弹窗。";

	private static final String TOAST_ENABLED = "Опция успешно включена";
	private static final String TOAST_ENABLED_EN = "选项已成功启用";

}