# ChronoMate

**ChronoMate** is a high-precision Android companion app for the **HT-X3000 / HT-X3006** airsoft chronograph. It transforms the basic web interface of the device into a comprehensive ballistic laboratory and marshaling toolkit.

## 🚀 Key Features

### 📊 Advanced Dashboard
- **Real-time Synchronization**: Automatically polls the chronograph every second via Wi-Fi.
- **Statistical Analysis**: Instant calculation of Average (AVG), Maximum (MAX), Minimum (MIN), Extreme Spread (ES), Standard Deviation (SD), and Variance (VAR).
- **Interactive Trend Chart**: A live velocity line graph with touch-interactive data points showing velocity and energy for every shot.

### 🛡️ Orga Chrono Mode
- **Marshaling Power**: Designed for field organizers to speed up the chrono station.
- **Joule Reference Grid**: Displays calculated kinetic energy for all standard BB weights (0.20g to 0.45g) simultaneously from a single shot.
- **Dynamic Limits**: Set your field's Joule limit and see the "Max Allowed BB Weight" for any given setup instantly.

### 🏹 Trajectory Simulation
- **Physics Engine**: Ported ballistic simulation (Gravity, Drag, and Magnus Effect).
- **Visual Flight Path**: See the impact of Hop-up (backspin) on your trajectory.
- **Target View**: An interactive reticle view that shows point-of-impact height (cm above/below) at specific distances (5m to 100m).
- **Optic Height**: Factor in your sight-over-bore height for surgical precision modeling.

### ⚙️ Customization & Persistence
- **Independent Calculations**: The app calculates Joules internally, ignoring clunky device settings.
- **Persistent Preferences**: Saves your default BB weight, Joule limits, and theme settings.
- **Dark & Light Mode**: High-contrast dark theme for low-light/workshop use and a high-readability light theme for outdoor use.
- **Auto-Connect**: Automatically attempts to join the "HT-X3000" Wi-Fi network on app launch.

## 🛠 Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Modern Declarative UI)
- **Architecture**: MVVM (Model-View-ViewModel)
- **Networking**: OkHttp3 for polling
- **Parsing**: Jsoup for real-time HTML data extraction
- **Graphics**: Compose Canvas for custom ballistic rendering

## 📸 Screenshots
*(Add your screenshots here)*

## 📥 Installation
1. Ensure your smartphone supports Wi-Fi.
2. Turn on your HT-X3000 Chronograph.
3. Open ChronoMate and grant the necessary Wi-Fi/Location permissions.
4. The app will automatically bridge to the device and start displaying data.

---
*Developed for Airsoft enthusiasts and event organizers.*
