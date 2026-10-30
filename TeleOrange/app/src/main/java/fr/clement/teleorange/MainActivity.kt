package fr.clement.teleorange

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import fr.clement.teleorange.OrangeDecoder.Key
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val bg = Executors.newSingleThreadExecutor()
    private val scanExec = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("tele", MODE_PRIVATE) }

    private var ip: String? = null
    private lateinit var status: TextView

    private val ORANGE = Color.parseColor("#FF7900")
    private val KEY_BG = Color.parseColor("#2A2A2E")
    private val SCREEN_BG = Color.parseColor("#121214")

    // ───────────────────────── cycle de vie ─────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = SCREEN_BG
        setContentView(buildUi())

        ip = prefs.getString("ip", null)
        if (ip == null) startScan() else checkSaved()
    }

    override fun onDestroy() {
        bg.shutdownNow(); scanExec.shutdownNow()
        super.onDestroy()
    }

    // ───────────────────────── détection ─────────────────────────

    private fun checkSaved() {
        val saved = ip ?: return
        setStatus("Connexion à $saved…", Color.LTGRAY)
        scanExec.execute {
            val ok = OrangeDecoder.probe(saved, 1500)
            ui.post {
                if (ok) setStatus("● Décodeur connecté ($saved)", Color.parseColor("#4CD964"))
                else setStatus("Décodeur introuvable à $saved — touche « Rechercher »", Color.parseColor("#FF5A5A"))
            }
        }
    }

    private fun startScan() {
        if (OrangeDecoder.localIpv4() == null) {
            setStatus("Active le Wi-Fi (même réseau que la Livebox)", Color.parseColor("#FF5A5A"))
            return
        }
        setStatus("Recherche du décodeur… 0 %", Color.LTGRAY)
        scanExec.execute {
            val found = OrangeDecoder.scan { p -> ui.post { if (ip == null || status.text.startsWith("Recherche")) setStatus("Recherche du décodeur… $p %", Color.LTGRAY) } }
            ui.post {
                if (found != null) {
                    ip = found
                    prefs.edit().putString("ip", found).apply()
                    setStatus("● Décodeur trouvé ($found)", Color.parseColor("#4CD964"))
                } else {
                    setStatus("Aucun décodeur trouvé — vérifie qu'il est allumé, ou saisis l'IP", Color.parseColor("#FF5A5A"))
                }
            }
        }
    }

    private fun askManualIp() {
        val input = EditText(this).apply {
            hint = "ex : 192.168.1.20"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(ip ?: "")
        }
        AlertDialog.Builder(this)
            .setTitle("Adresse IP du décodeur")
            .setMessage("Tu la trouves dans l'interface de la Livebox (http://192.168.1.1 → Mon réseau).")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val v = input.text.toString().trim()
                if (v.isNotEmpty()) {
                    ip = v
                    prefs.edit().putString("ip", v).apply()
                    checkSaved()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ───────────────────────── envoi des touches ─────────────────────────

    private fun send(key: Int, mode: Int = 0) {
        val target = ip
        if (target == null) { setStatus("Pas encore de décodeur — touche « Rechercher »", Color.parseColor("#FF5A5A")); return }
        bg.execute {
            if (!OrangeDecoder.sendKey(target, key, mode)) {
                ui.post { setStatus("Pas de réponse de $target", Color.parseColor("#FF5A5A")) }
            }
        }
    }

    // ───────────────────────── interface ─────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun setStatus(text: String, color: Int) {
        status.text = text; status.setTextColor(color)
    }

    private fun round(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    /** Bouton de télécommande. repeat = l'appui maintenu répète la touche (volume, chaînes, flèches). */
    private fun key(label: String, code: Int, color: Int = KEY_BG, textSize: Float = 18f, repeat: Boolean = false): Button =
        Button(this).apply {
            text = label
            this.textSize = textSize
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = round(color, 16)
            stateListAnimator = null
            if (!repeat) {
                setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); send(code) }
            } else {
                val repeater = object : Runnable {
                    override fun run() { send(code); ui.postDelayed(this, 180) }
                }
                setOnTouchListener { v, e ->
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            v.isPressed = true; v.alpha = 0.6f
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            send(code); ui.postDelayed(repeater, 450)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.isPressed = false; v.alpha = 1f
                            ui.removeCallbacks(repeater)
                        }
                    }
                    true
                }
            }
        }

    private fun row(vararg views: View?, height: Int = 60): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(height)).apply { topMargin = dp(10) }
            for (v in views) {
                val child = v ?: View(this@MainActivity)   // null = case vide
                addView(child, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply {
                    marginStart = dp(5); marginEnd = dp(5)
                })
            }
        }

    private fun smallText(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.GRAY); textSize = 12f; gravity = Gravity.CENTER
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(24))
            setBackgroundColor(SCREEN_BG)
        }

        // En-tête
        root.addView(TextView(this).apply {
            text = "Télécommande"
            textSize = 24f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE)
        })
        status = TextView(this).apply { textSize = 13f; setPadding(0, dp(4), 0, dp(4)) }
        root.addView(status)

        val tools = row(
            Button(this).apply {
                text = "Rechercher"; isAllCaps = false; setTextColor(Color.WHITE); textSize = 14f
                background = round(Color.parseColor("#333338"), 12)
                setOnClickListener { ip = null; startScan() }
            },
            Button(this).apply {
                text = "Saisir l'IP"; isAllCaps = false; setTextColor(Color.WHITE); textSize = 14f
                background = round(Color.parseColor("#333338"), 12)
                setOnClickListener { askManualIp() }
            },
            height = 42
        )
        root.addView(tools)

        // Power / Menu / Retour
        root.addView(row(key("⏻", Key.POWER, ORANGE, 22f), key("Menu", Key.MENU), key("Retour", Key.BACK)))

        // Pavé directionnel
        root.addView(row(null, key("▲", Key.UP, repeat = true), null, height = 64))
        root.addView(row(key("◀", Key.LEFT, repeat = true), key("OK", Key.OK, ORANGE, 20f), key("▶", Key.RIGHT, repeat = true), height = 64))
        root.addView(row(null, key("▼", Key.DOWN, repeat = true), null, height = 64))

        // Volume / chaînes
        root.addView(row(key("Vol +", Key.VOL_UP, repeat = true), key("🔇", Key.MUTE), key("Ch +", Key.CH_UP, repeat = true)))
        root.addView(row(key("Vol −", Key.VOL_DOWN, repeat = true), key("VOD", Key.VOD, textSize = 15f), key("Ch −", Key.CH_DOWN, repeat = true)))

        // Lecture
        root.addView(row(key("⏪", Key.REWIND), key("⏯", Key.PLAY_PAUSE), key("⏩", Key.FORWARD), key("⏺", Key.REC, Color.parseColor("#8A1C1C"))))

        // Pavé numérique
        root.addView(row(key("1", Key.digit(1)), key("2", Key.digit(2)), key("3", Key.digit(3))))
        root.addView(row(key("4", Key.digit(4)), key("5", Key.digit(5)), key("6", Key.digit(6))))
        root.addView(row(key("7", Key.digit(7)), key("8", Key.digit(8)), key("9", Key.digit(9))))
        root.addView(row(null, key("0", Key.digit(0)), null))

        root.addView(smallText("Sans publicité · le téléphone doit être sur le Wi-Fi de la Livebox").apply {
            setPadding(0, dp(18), 0, 0)
        })

        return ScrollView(this).apply {
            setBackgroundColor(SCREEN_BG)
            isFillViewport = true
            addView(root, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }
}
