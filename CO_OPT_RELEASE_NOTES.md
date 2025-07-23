# SOOTHSAYER ATAK Plugin - Co-Opt Feature Release Notes

## 🚀 Major Feature Enhancement: Advanced Co-Opt Functionality

### Overview
The Co-Opt feature has been completely redesigned and enhanced to provide tactical operators with powerful capabilities for real-time radio frequency propagation analysis using existing map markers and contacts.

---

## 🆕 New Features

### 1. **Universal Marker Support**
- **Contact Integration**: Automatically includes all team contacts from the contacts list
- **Self Marker**: Includes your own position marker for analysis
- **CoT Marker Support**: Captures all Cursor-on-Target markers from the map
- **Smart Filtering**: Excludes plugin-generated markers to avoid conflicts
- **Tactical Symbol Recognition**: Properly identifies and displays MIL-STD-2525 symbols

### 2. **Enhanced Marker Selection Interface**
- **Organized List Structure**: 
  - Contacts section (alphabetically sorted) at the top
  - CoT markers section (newest first) below
- **Real-time Search**: Filter markers by name, callsign, or UID
- **Persistent Selections**: Checkbox states and template selections saved between sessions
- **Smart Sorting**: Checked/enabled markers automatically move to the top of the list

### 3. **Advanced Icon Rendering**
- **Intelligent Icon Detection**: 
  - Colored shapes (circles/squares) for contacts with color metadata
  - Complex tactical symbols for CoT markers with MIL-STD types
  - Square shapes for hostile markers, circles for friendly/neutral
- **Proper Symbol Scaling**: 24dp icons with correct aspect ratios
- **Fallback Handling**: Graceful degradation when icons aren't available

### 4. **Interactive Map Integration**
- **Tap-to-Jump**: Tap any marker on the map to instantly scroll to it in the co-opt list
- **Visual Flash Indicator**: 0.8-second green glow highlights the found marker
- **Smart Positioning**: Selected marker appears at the top of the visible area
- **Search-Aware**: Works with filtered lists and respects current search queries

### 5. **Automated Tracking & Updates**
- **Time-Based Updates**: Configurable interval-based recalculation (default: 5 minutes)
- **Distance-Based Updates**: Movement threshold triggers (configurable in meters)
- **Real-time Position Sync**: Automatically updates lat/lon coordinates as markers move
- **Fixed Height Operations**: Standardized 2m antenna height for consistent ground operations
- **Intelligent Initialization**: Proper baseline position tracking for accurate distance calculations

### 6. **Template Management**
- **Persistent Template Selection**: Radio templates saved per marker across sessions
- **Dropdown Integration**: Reuses existing template spinner styling and functionality
- **Bulk Configuration**: Configure multiple markers simultaneously
- **Template Validation**: Only valid templates with proper data are selectable

---

## 🔧 Technical Improvements

### Data Persistence
- **Checkbox States**: Stored in preferences as JSON (`co_opt_checkbox_selections`)
- **Template Selections**: Stored in preferences as JSON (`co_opt_radio_selections`)
- **Session Recovery**: Automatically restores previous configurations on dialog open

### Performance Optimizations
- **Efficient Marker Collection**: Recursive map traversal with smart filtering
- **Lazy Loading**: Template adapters created on-demand
- **Memory Management**: Proper cleanup of tracking handlers and listeners

### User Experience Enhancements
- **Compact UI Design**: Reduced padding and optimized layout for tactical environments
- **Fast Animations**: 0.8-second flash duration for quick tactical feedback
- **Centered Text**: Professional appearance with centered callsign display
- **No Toast Spam**: Reduced unnecessary notifications for cleaner operation

---

## 🛠️ Configuration Options

### Time-Based Tracking
- **Refresh Interval**: Configurable seconds between automatic updates
- **Countdown Display**: Real-time countdown showing next update time
- **Manual Override**: Stop button to disable tracking when needed

### Distance-Based Tracking
- **Movement Threshold**: Configurable meters before triggering recalculation
- **Position Baseline**: Proper initialization of starting positions
- **Smart Detection**: Only recalculates when actual movement exceeds threshold

---

## 📋 Operational Workflow

1. **Open Co-Opt Dialog**: Click the Co-Opt button from main interface
2. **Browse Available Markers**: View organized list of contacts and CoT markers
3. **Search & Filter**: Use search bar to quickly find specific markers
4. **Select Markers**: Check boxes for markers to include in analysis
5. **Assign Templates**: Select appropriate radio templates from dropdowns
6. **Configure Tracking**: Set time and/or distance-based update preferences
7. **Activate**: Click OK to begin real-time tracking and analysis
8. **Monitor**: Watch countdown timer and automatic recalculations
9. **Manage**: Checked markers stay at top for easy access and modification

---

## 🎯 Tactical Benefits

### Situational Awareness
- **Real-time RF Analysis**: Continuous propagation modeling as units move
- **Multi-unit Coordination**: Simultaneous tracking of multiple friendly positions
- **Dynamic Planning**: Instant recalculation as tactical situation evolves

### Operational Efficiency
- **Reduced Setup Time**: Automatic marker detection eliminates manual entry
- **Persistent Configuration**: Settings survive app restarts and reconnections
- **Quick Access**: Tap-to-jump and sorting features speed up operations

### Mission Flexibility
- **Adaptive Tracking**: Choose time-based, distance-based, or manual updates
- **Template Variety**: Apply different radio profiles to different units
- **Scalable Operations**: Handle small teams or large-scale deployments

---

## 🔒 Data Management

### Privacy & Security
- **Local Storage**: All preferences stored locally on device
- **No External Transmission**: Marker data never leaves the device
- **Secure Cleanup**: Proper disposal of tracking data when features disabled

### Reliability
- **Error Handling**: Graceful fallbacks for missing or invalid data
- **State Recovery**: Automatic restoration of interrupted tracking sessions
- **Validation**: Input verification prevents invalid configurations

---

## 🚦 System Requirements

- **ATAK Version**: Compatible with ATAK-CIV-5.3.0.12-SDK
- **Android Version**: Minimum API level as per ATAK requirements
- **Memory**: Optimized for tactical device constraints
- **Network**: CloudRF API access required for propagation calculations

---

## 📝 Technical Notes

### Performance Considerations
- **Marker Limits**: Efficiently handles hundreds of markers
- **Update Frequency**: Configurable to balance accuracy vs. battery life
- **Memory Usage**: Minimal overhead with proper cleanup

### Integration Points
- **ATAK Contacts**: Full integration with ATAK contact management
- **Map Events**: Seamless interaction with map tap events
- **Preferences**: Uses ATAK preference system for persistence

---

## 🔮 Future Roadmap

### Planned Enhancements
The following features are planned for future releases to further enhance the SOOTHSAYER plugin's capabilities:

### 1. **Historical Analysis & Replay**
- **Calculation Breadcrumbs**: Ability to store and replay previous calculation runs
- **Heatmap Replay**: Full visualization replay of historical propagation analysis
- **Session History**: Track and revisit past operational scenarios

### 2. **Enhanced User Interface**
- **Quick Radio Selection**: Tap-to-change radio templates directly from dropdown lists
- **Direct Map Navigation**: Tap CoT icons in menus to instantly navigate to map locations
- **Streamlined Workflows**: Reduced click paths for common operations

### 3. **Collaborative Features**
- **SOOTHSAYER Groups**: Automatic group assignment for plugin users
- **Radio List Sharing**: Send and discover radio configuration lists between team members
- **Cross-Platform Sync**: Share templates and configurations across devices

### 4. **Real-time Position Tracking**
- **CoT Movement Integration**: Automatic position updates when markers are moved via ATAK
- **Live Tracking Sync**: Seamless integration with ATAK's native movement tracking
- **Dynamic Recalculation**: Instant propagation updates as tactical situations evolve

---

*This release represents a significant enhancement to the SOOTHSAYER plugin's tactical capabilities, providing operators with unprecedented real-time RF analysis tools integrated directly into their operational workflow.* 