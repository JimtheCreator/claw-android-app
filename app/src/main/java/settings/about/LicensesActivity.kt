package settings.about

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.claw.ai.R
import com.claw.ai.databinding.ActivityLicensesBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

data class LibraryInfo(
    val name: String,
    val author: String,
    val description: String,
    val version: String,
    val licenseKey: String,
    val category: String,
    val icon: String // Emoji icon for visual appeal
)

class LicensesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLicensesBinding
    private lateinit var librariesAdapter: LibrariesAdapter

    // Define license texts as a map to avoid resource reflection
    private val licenseTexts = mapOf(
        "apache_2_license" to R.string.apache_2_license,
        "mit_license" to R.string.mit_license,
        "bsd_license" to R.string.bsd_license
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLicensesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fix the window insets to prevent content from being hidden
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Only apply top insets to the main container, let NestedScrollView handle the rest
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        setupToolbar()
        setupRecyclerView()
        animateEntrance()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecyclerView() {
        librariesAdapter = LibrariesAdapter(getLibrariesList()) { library ->
            showLicenseBottomSheet(library)
        }
        binding.librariesRecyclerview.apply {
            layoutManager = LinearLayoutManager(this@LicensesActivity)
            adapter = librariesAdapter
            setHasFixedSize(true)
        }
    }

    private fun getLibrariesList(): List<LibraryInfo> {
        return listOf(
            LibraryInfo(
                name = "Glide",
                author = "Bumptech",
                description = "Fast and efficient image loading framework",
                version = "4.16.0",
                licenseKey = "apache_2_license",
                category = "Image Loading",
                icon = "🖼️"
            ),
            LibraryInfo(
                name = "Lottie Android",
                author = "Airbnb",
                description = "Render After Effects animations natively",
                version = "6.4.0",
                licenseKey = "apache_2_license",
                category = "Animation",
                icon = "✨"
            ),
            LibraryInfo(
                name = "Retrofit",
                author = "Square",
                description = "Type-safe HTTP client for Android and Kotlin",
                version = "2.9.0",
                licenseKey = "apache_2_license",
                category = "Networking",
                icon = "🌐"
            ),
            LibraryInfo(
                name = "OkHttp",
                author = "Square",
                description = "Efficient HTTP & HTTP/2 client for Android",
                version = "4.12.0",
                licenseKey = "apache_2_license",
                category = "Networking",
                icon = "📡"
            ),
            LibraryInfo(
                name = "RxJava",
                author = "ReactiveX",
                description = "Reactive Extensions for the JVM",
                version = "2.2.21",
                licenseKey = "apache_2_license",
                category = "Reactive Programming",
                icon = "⚡"
            ),
            LibraryInfo(
                name = "RxAndroid",
                author = "ReactiveX",
                description = "Android specific bindings for RxJava",
                version = "2.1.1",
                licenseKey = "apache_2_license",
                category = "Reactive Programming",
                icon = "🤖"
            ),
            LibraryInfo(
                name = "Room",
                author = "Google",
                description = "SQLite object mapping library",
                version = "2.7.1",
                licenseKey = "apache_2_license",
                category = "Database",
                icon = "🗄️"
            ),
            LibraryInfo(
                name = "Firebase SDK",
                author = "Google",
                description = "Backend-as-a-Service platform",
                version = "Multiple",
                licenseKey = "apache_2_license",
                category = "Backend Services",
                icon = "🔥"
            ),
            LibraryInfo(
                name = "MPAndroidChart",
                author = "PhilJay",
                description = "Powerful & easy to use chart library",
                version = "3.1.0",
                licenseKey = "apache_2_license",
                category = "Data Visualization",
                icon = "📊"
            ),
            LibraryInfo(
                name = "TradingView Charts",
                author = "TradingView",
                description = "Lightweight financial charts",
                version = "3.8.0",
                licenseKey = "apache_2_license",
                category = "Data Visualization",
                icon = "📈"
            ),
            LibraryInfo(
                name = "Stripe Android",
                author = "Stripe",
                description = "Accept payments in mobile apps",
                version = "21.13.0",
                licenseKey = "mit_license",
                category = "Payments",
                icon = "💳"
            ),
            LibraryInfo(
                name = "Shimmer",
                author = "Facebook",
                description = "Easy shimmer effect for loading states",
                version = "0.5.0",
                licenseKey = "bsd_license",
                category = "UI Effects",
                icon = "✨"
            ),
            LibraryInfo(
                name = "Timber",
                author = "Jake Wharton",
                description = "Logger with a small, extensible API",
                version = "5.0.1",
                licenseKey = "apache_2_license",
                category = "Logging",
                icon = "🪵"
            ),
            LibraryInfo(
                name = "CircularImageView",
                author = "Mikhaël Lopez",
                description = "Custom circular ImageView for Android",
                version = "4.3.1",
                licenseKey = "apache_2_license",
                category = "UI Components",
                icon = "⭕"
            ),
            LibraryInfo(
                name = "CircleProgress",
                author = "lzyzsd",
                description = "Circular progress bar for Android",
                version = "1.2.1",
                licenseKey = "apache_2_license",
                category = "UI Components",
                icon = "🔄"
            )
        )
    }

    private fun showLicenseBottomSheet(library: LibraryInfo) {
        val bottomSheetDialog = BottomSheetDialog(this, R.style.BottomSheetDialogThemeLicense)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_license, null)

        // Populate the bottom sheet with library info
        bottomSheetView.findViewById<TextView>(R.id.library_name).text = library.name
        bottomSheetView.findViewById<TextView>(R.id.library_author).text =
            getString(R.string.library_author_format, library.author)
        bottomSheetView.findViewById<TextView>(R.id.library_version).text =
            getString(R.string.library_version_format, library.version)
        bottomSheetView.findViewById<TextView>(R.id.library_description).text = library.description
        val closeDialog = bottomSheetView.findViewById<MaterialButton>(R.id.close_bottom_sheet)

        // Get license text using proper resource mapping (no reflection)
        val licenseText = licenseTexts[library.licenseKey]?.let { stringRes ->
            getString(stringRes)
        } ?: "License text not available"

        bottomSheetView.findViewById<TextView>(R.id.license_text).text = licenseText

        bottomSheetDialog.setContentView(bottomSheetView)

        // Make the bottom sheet appear at full height
        bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheetDialog.behavior.skipCollapsed = true
        bottomSheetDialog.behavior.isHideable = true

        closeDialog.setOnClickListener{
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun animateEntrance() {
        // Add some delightful entrance animations
        val titleView = findViewById<TextView>(R.id.title_text)
        val recyclerView = findViewById<RecyclerView>(R.id.libraries_recyclerview)

        titleView.alpha = 0f
        titleView.translationY = -50f
        recyclerView.alpha = 0f
        recyclerView.translationY = 100f

        val titleAnimator =
            ObjectAnimator.ofFloat(titleView, "alpha", 0f, 1f).apply { duration = 600 }
        val titleTranslateAnimator =
            ObjectAnimator.ofFloat(titleView, "translationY", -50f, 0f).apply { duration = 600 }

        val recyclerAlphaAnimator = ObjectAnimator.ofFloat(recyclerView, "alpha", 0f, 1f).apply {
            duration = 800
            startDelay = 200
        }
        val recyclerTranslateAnimator =
            ObjectAnimator.ofFloat(recyclerView, "translationY", 100f, 0f).apply {
                duration = 800
                startDelay = 200
            }

        AnimatorSet().apply {
            playTogether(
                titleAnimator,
                titleTranslateAnimator,
                recyclerAlphaAnimator,
                recyclerTranslateAnimator
            )
            start()
        }
    }
}

// LibrariesAdapter remains the same
class LibrariesAdapter(
    private val libraries: List<LibraryInfo>,
    private val onLibraryClick: (LibraryInfo) -> Unit
) : RecyclerView.Adapter<LibrariesAdapter.ViewHolder>() {

    private val categoryColors = mapOf(
        "UI Framework" to R.color.category_ui,
        "Image Loading" to R.color.category_media,
        "Animation" to R.color.category_animation,
        "Networking" to R.color.category_network,
        "Reactive Programming" to R.color.category_reactive,
        "Database" to R.color.category_database,
        "Backend Services" to R.color.category_backend,
        "Data Visualization" to R.color.category_charts,
        "Payments" to R.color.category_payments,
        "UI Effects" to R.color.category_effects,
        "Logging" to R.color.category_logging,
        "UI Components" to R.color.category_components
    )

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.library_card)
        val name: TextView = view.findViewById(R.id.library_name)
        val author: TextView = view.findViewById(R.id.library_author)
        val description: TextView = view.findViewById(R.id.library_description)
        val version: TextView = view.findViewById(R.id.library_version)
        val category: TextView = view.findViewById(R.id.library_category)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val library = libraries[position]

        holder.name.text = library.name
        holder.author.text = "by ${library.author}"
        holder.description.text = library.description
        holder.version.text = "v${library.version}"
        holder.category.text = library.category

        // Set category color
        val colorRes = categoryColors[library.category] ?: R.color.category_default
        holder.category.setBackgroundColor(
            ContextCompat.getColor(
                holder.itemView.context,
                colorRes
            )
        )

        // Add click animation and listener
        holder.card.setOnClickListener {
            // Scale animation
            val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
                holder.card,
                android.animation.PropertyValuesHolder.ofFloat("scaleX", 0.95f),
                android.animation.PropertyValuesHolder.ofFloat("scaleY", 0.95f)
            ).apply { duration = 100 }

            val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
                holder.card,
                android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f),
                android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f)
            ).apply { duration = 100 }

            AnimatorSet().apply {
                play(scaleDown).before(scaleUp)
                start()
            }

            onLibraryClick(library)
        }

        // Stagger animation for items
        holder.card.alpha = 0f
        holder.card.translationX = 100f
        holder.card.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(400)
            .setStartDelay((position * 50).toLong())
            .start()
    }

    override fun getItemCount() = libraries.size
}