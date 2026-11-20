package com.mumbaishareauto.app.data

import com.mumbaishareauto.app.model.RailwayLine
import com.mumbaishareauto.app.model.StationSide
import com.mumbaishareauto.app.model.StationStandRoute

/**
 * Reference directory of designated Mumbai railway station share-auto stands.
 *
 * IMPORTANT PRODUCT COMPLIANCE:
 * 1. Physical station stand locations, landmarks, coordinates, and journey times are unverified
 *    reference data provided solely for commuter and driver orientation.
 * 2. Does not represent live driver matching, active drivers, or live seat availability.
 * 3. Actual fares are POSTED BY DRIVERS as fixed TOTAL FARE PER BOOKING, never per-seat multiplication.
 */
object MumbaiStationDirectory {

    val allStationRoutes: List<StationStandRoute> = listOf(
        // ==================== WESTERN LINE ====================
        // Bandra
        StationStandRoute(
            id = "w_bnd_e_bkc",
            stationName = "Bandra",
            stationCode = "BA",
            line = RailwayLine.WESTERN,
            side = StationSide.EAST,
            standName = "Bandra Station East Auto Stand",
            standLandmark = "Outside Skywalk / Platform 1 Exit, near Station Rd & BEST Stop",
            destination = "BKC (Bharat Diamond Bourse / ICICI / MMRDA)",
            viaPoints = listOf("Kalanagar", "Family Court", "Canara Bank BKC"),
            distanceKm = 3.2,
            approxMinutes = 12,
            referenceIndicativeFare = 30.0,
            standLatitude = 19.0553,
            standLongitude = 72.8427
        ),
        StationStandRoute(
            id = "w_bnd_e_kalanagar",
            stationName = "Bandra",
            stationCode = "BA",
            line = RailwayLine.WESTERN,
            side = StationSide.EAST,
            standName = "Bandra East Kalanagar Stand",
            standLandmark = "Near Skywalk Foot, East Ticket Booking Counter",
            destination = "Kalanagar / MHADA Office / Asian Heart",
            viaPoints = listOf("Survey Office", "MHADA HQ"),
            distanceKm = 1.4,
            approxMinutes = 6,
            referenceIndicativeFare = 20.0,
            standLatitude = 19.0560,
            standLongitude = 72.8435
        ),
        StationStandRoute(
            id = "w_bnd_w_bandstand",
            stationName = "Bandra",
            stationCode = "BA",
            line = RailwayLine.WESTERN,
            side = StationSide.WEST,
            standName = "Bandra West Station Stand",
            standLandmark = "Opposite Lucky Restaurant, Station Road Exit",
            destination = "Bandstand Promenade / Mannat / Mount Mary",
            viaPoints = listOf("Hill Road", "Mehboob Studio", "Taj Lands End"),
            distanceKm = 2.8,
            approxMinutes = 10,
            referenceIndicativeFare = 30.0,
            standLatitude = 19.0544,
            standLongitude = 72.8402
        ),
        StationStandRoute(
            id = "w_bnd_w_carter",
            stationName = "Bandra",
            stationCode = "BA",
            line = RailwayLine.WESTERN,
            side = StationSide.WEST,
            standName = "Bandra West Linking Rd Stand",
            standLandmark = "Near Platform 4 Footbridge, Turner Road Corner",
            destination = "Carter Road / Khar Danda",
            viaPoints = listOf("Waterfield Rd", "Pali Naka", "Otters Club"),
            distanceKm = 3.1,
            approxMinutes = 11,
            referenceIndicativeFare = 30.0,
            standLatitude = 19.0570,
            standLongitude = 72.8385
        ),

        // Santacruz
        StationStandRoute(
            id = "w_scz_e_kalina",
            stationName = "Santacruz",
            stationCode = "STC",
            line = RailwayLine.WESTERN,
            side = StationSide.EAST,
            standName = "Santacruz East Station Stand",
            standLandmark = "Outside East Booking Office, near Vakola Bridge",
            destination = "Kalina Campus (Mumbai University) / Grand Hyatt",
            viaPoints = listOf("Vakola Police Station", "Military Camp", "CST Road"),
            distanceKm = 2.4,
            approxMinutes = 9,
            referenceIndicativeFare = 25.0,
            standLatitude = 19.0818,
            standLongitude = 72.8436
        ),
        StationStandRoute(
            id = "w_scz_w_juhu",
            stationName = "Santacruz",
            stationCode = "STC",
            line = RailwayLine.WESTERN,
            side = StationSide.WEST,
            standName = "Santacruz West Station Stand",
            standLandmark = "Opposite Station Market, SV Road Crossing",
            destination = "Juhu Beach / SNDT Women's University",
            viaPoints = listOf("Juhu Tara Road", "Maneckji Cooper School"),
            distanceKm = 3.0,
            approxMinutes = 12,
            referenceIndicativeFare = 30.0,
            standLatitude = 19.0825,
            standLongitude = 72.8390
        ),

        // Vile Parle
        StationStandRoute(
            id = "w_vpl_e_airport",
            stationName = "Vile Parle",
            stationCode = "VLP",
            line = RailwayLine.WESTERN,
            side = StationSide.EAST,
            standName = "Vile Parle East Stand",
            standLandmark = "Nehru Road, outside Platform 1 East gate",
            destination = "Domestic Airport Terminal 1 (T1) / WE Highway",
            viaPoints = listOf("Hanuman Road", "Sahara Star Hotel"),
            distanceKm = 2.2,
            approxMinutes = 8,
            referenceIndicativeFare = 30.0,
            standLatitude = 19.0988,
            standLongitude = 72.8475
        ),
        StationStandRoute(
            id = "w_vpl_w_mithibai",
            stationName = "Vile Parle",
            stationCode = "VLP",
            line = RailwayLine.WESTERN,
            side = StationSide.WEST,
            standName = "Vile Parle West Stand",
            standLandmark = "Station Road, opposite Prime Mall exit",
            destination = "Mithibai / NMIMS College / Cooper Hospital / Juhu",
            viaPoints = listOf("Irla Market", "JVPD Scheme", "Gulmohar Road"),
            distanceKm = 2.0,
            approxMinutes = 7,
            referenceIndicativeFare = 25.0,
            standLatitude = 19.1001,
            standLongitude = 72.8420
        ),

        // Andheri
        StationStandRoute(
            id = "w_adh_e_seepz",
            stationName = "Andheri",
            stationCode = "ADH",
            line = RailwayLine.WESTERN,
            side = StationSide.EAST,
            standName = "Andheri East Auto Deck Stand",
            standLandmark = "Outside Platform 1, Elevated Auto Deck & Metro Gate 2",
            destination = "SEEPZ Gate 1 & 2 / MIDC / Mahakali Caves",
            viaPoints = listOf("Chakala Metro", "Pump House", "MIDC Central Road"),
            distanceKm = 4.5,
            approxMinutes = 15,
            referenceIndicativeFare = 30.0,
            standLatitude = 19.1197,
            standLongitude = 72.8464
        ),
        StationStandRoute(
            id = "w_adh_e_marol",
            stationName = "Andheri",
            stationCode = "ADH",
            line = RailwayLine.WESTERN,
            side = StationSide.EAST,
            standName = "Andheri East Kurla Road Stand",
            standLandmark = "Near Best Bus Depot & Railway Skywalk exit",
            destination = "Marol Naka / Airport Road / Leela Hotel",
            viaPoints = listOf("Kabra Metro", "JB Nagar", "Marol Fire Brigade"),
            distanceKm = 3.8,
            approxMinutes = 14,
            referenceIndicativeFare = 30.0,
            standLatitude = 19.1190,
            standLongitude = 72.8470
        ),
        StationStandRoute(
            id = "w_adh_w_lokhandwala",
            stationName = "Andheri",
            stationCode = "ADH",
            line = RailwayLine.WESTERN,
            side = StationSide.WEST,
            standName = "Andheri West Station Stand",
            standLandmark = "Outside Platform 8, Opposite BEST Depot & SV Road",
            destination = "Lokhandwala Complex / Market / Celebrations Club",
            viaPoints = listOf("Shoppers Stop", "Four Bungalows", "Samarth Nagar"),
            distanceKm = 3.5,
            approxMinutes = 14,
            referenceIndicativeFare = 30.0,
            standLatitude = 19.1205,
            standLongitude = 72.8420
        ),
        StationStandRoute(
            id = "w_adh_w_versova",
            stationName = "Andheri",
            stationCode = "ADH",
            line = RailwayLine.WESTERN,
            side = StationSide.WEST,
            standName = "Andheri West Versova Stand",
            standLandmark = "Near Metro Pillar 24, SV Road & JP Road junction",
            destination = "Versova Jetty / Yari Road / Nana Nani Park",
            viaPoints = listOf("DN Nagar Metro", "Seven Bungalows", "Model Town"),
            distanceKm = 4.2,
            approxMinutes = 16,
            referenceIndicativeFare = 35.0,
            standLatitude = 19.1210,
            standLongitude = 72.8415
        ),

        // Goregaon
        StationStandRoute(
            id = "w_gor_e_nesco",
            stationName = "Goregaon",
            stationCode = "GMN",
            line = RailwayLine.WESTERN,
            side = StationSide.EAST,
            standName = "Goregaon East Station Stand",
            standLandmark = "Under Station Flyover, Platform 1 East Exit",
            destination = "NESCO Exhibition Centre / Nirlon Knowledge Park / Hub Mall",
            viaPoints = listOf("Western Express Highway", "Vanrai Police Station"),
            distanceKm = 2.1,
            approxMinutes = 8,
            referenceIndicativeFare = 25.0,
            standLatitude = 19.1645,
            standLongitude = 72.8492
        ),
        StationStandRoute(
            id = "w_gor_e_filmcity",
            stationName = "Goregaon",
            stationCode = "GMN",
            line = RailwayLine.WESTERN,
            side = StationSide.EAST,
            standName = "Goregaon East Gokuldham Stand",
            standLandmark = "East Ticket Counter, Gen. AK Vaidya Marg",
            destination = "Film City Gate / Gokuldham / Oberoi Mall",
            viaPoints = listOf("Dindoshi", "Oberoi Woods", "Santosh Nagar"),
            distanceKm = 4.8,
            approxMinutes = 18,
            referenceIndicativeFare = 35.0,
            standLatitude = 19.1650,
            standLongitude = 72.8500
        ),

        // Malad
        StationStandRoute(
            id = "w_mld_w_mindspace",
            stationName = "Malad",
            stationCode = "MDD",
            line = RailwayLine.WESTERN,
            side = StationSide.WEST,
            standName = "Malad West Station Stand",
            standLandmark = "Outside Platform 1 West, Near Natraj Market & SV Road",
            destination = "Mindspace IT Park / Inorbit Mall / Infinity Mall",
            viaPoints = listOf("Under Liberty Bridge", "Chincholi Bunder", "Link Road"),
            distanceKm = 2.5,
            approxMinutes = 10,
            referenceIndicativeFare = 25.0,
            standLatitude = 19.1865,
            standLongitude = 72.8480
        ),

        // Borivali
        StationStandRoute(
            id = "w_bvi_w_gorai",
            stationName = "Borivali",
            stationCode = "BVI",
            line = RailwayLine.WESTERN,
            side = StationSide.WEST,
            standName = "Borivali West Station Stand",
            standLandmark = "Opposite Platform 1 West / Babhai Naka exit",
            destination = "Gorai Creek Jetty (EsselWorld) / Gorai Sector 2",
            viaPoints = listOf("Don Bosco School", "Pragati Nagar", "Gorai Bridge"),
            distanceKm = 4.6,
            approxMinutes = 16,
            referenceIndicativeFare = 35.0,
            standLatitude = 19.2295,
            standLongitude = 72.8565
        ),
        StationStandRoute(
            id = "w_bvi_e_nationalpark",
            stationName = "Borivali",
            stationCode = "BVI",
            line = RailwayLine.WESTERN,
            side = StationSide.EAST,
            standName = "Borivali East Station Stand",
            standLandmark = "Outside Platform 8/9 East, Kasturba Cross Road",
            destination = "Sanjay Gandhi National Park Gate / Magathane",
            viaPoints = listOf("WE Highway Flyover", "Kulupwadi", "Sukur Garden"),
            distanceKm = 2.2,
            approxMinutes = 8,
            referenceIndicativeFare = 25.0,
            standLatitude = 19.2285,
            standLongitude = 72.8600
        ),

        // ==================== CENTRAL LINE ====================
        // Dadar
        StationStandRoute(
            id = "c_ddr_e_parel",
            stationName = "Dadar",
            stationCode = "DR",
            line = RailwayLine.CENTRAL,
            side = StationSide.EAST,
            standName = "Dadar Central East Stand",
            standLandmark = "Outside Swami Narayan Temple & Station Flyover exit",
            destination = "Parel / KEM Hospital / Tata Memorial / Bhoiwada",
            viaPoints = listOf("Chitra Cinema", "Hindmata Flyover", "Wadia Hospital"),
            distanceKm = 2.3,
            approxMinutes = 10,
            referenceIndicativeFare = 25.0,
            standLatitude = 19.0185,
            standLongitude = 72.8440
        ),

        // Kurla
        StationStandRoute(
            id = "c_cla_w_phoenix",
            stationName = "Kurla",
            stationCode = "CLA",
            line = RailwayLine.CENTRAL,
            side = StationSide.WEST,
            standName = "Kurla West Station Stand",
            standLandmark = "Outside Platform 1 West Exit, Near Auto Stand",
            destination = "Phoenix Marketcity Mall / LBS Marg / Kamani",
            viaPoints = listOf("Kurla Depot", "Sheetal Cinema", "Bail Bazar"),
            distanceKm = 2.2,
            approxMinutes = 9,
            referenceIndicativeFare = 25.0,
            standLatitude = 19.0660,
            standLongitude = 72.8800
        ),
        StationStandRoute(
            id = "c_cla_w_bkc",
            stationName = "Kurla",
            stationCode = "CLA",
            line = RailwayLine.CENTRAL,
            side = StationSide.WEST,
            standName = "Kurla West BKC Stand",
            standLandmark = "Opposite Station Auto Deck, SG Barve Marg junction",
            destination = "BKC MTNL / Canara Bank / Bharat Diamond Bourse",
            viaPoints = listOf("Kurla Telephone Exchange", "City Park BKC"),
            distanceKm = 3.0,
            approxMinutes = 12,
            referenceIndicativeFare = 30.0,
            standLatitude = 19.0665,
            standLongitude = 72.8790
        ),

        // Ghatkopar
        StationStandRoute(
            id = "c_ghk_w_rcity",
            stationName = "Ghatkopar",
            stationCode = "GC",
            line = RailwayLine.CENTRAL,
            side = StationSide.WEST,
            standName = "Ghatkopar West Station Stand",
            standLandmark = "Outside Metro Station Gate 1 & Platform 1 West exit",
            destination = "R-City Mall / Amrut Nagar / LBS Marg",
            viaPoints = listOf("LBS Road Crossing", "Sarvodaya Hospital"),
            distanceKm = 2.8,
            approxMinutes = 11,
            referenceIndicativeFare = 30.0,
            standLatitude = 19.0865,
            standLongitude = 72.9065
        ),

        // Kanjurmarg
        StationStandRoute(
            id = "c_kjm_w_powai",
            stationName = "Kanjurmarg",
            stationCode = "KJMG",
            line = RailwayLine.CENTRAL,
            side = StationSide.WEST,
            standName = "Kanjurmarg West Station Stand",
            standLandmark = "Outside Station Skywalk West exit, JVLR junction",
            destination = "Powai Hiranandani / IIT Bombay Main Gate / Galleria",
            viaPoints = listOf("L&T Gate 5", "Gandhi Nagar", "Kailash Complex"),
            distanceKm = 4.0,
            approxMinutes = 14,
            referenceIndicativeFare = 35.0,
            standLatitude = 19.1285,
            standLongitude = 72.9280
        ),

        // Thane
        StationStandRoute(
            id = "c_tna_w_wagle",
            stationName = "Thane",
            stationCode = "TNA",
            line = RailwayLine.CENTRAL,
            side = StationSide.WEST,
            standName = "Thane West SATIS Auto Stand",
            standLandmark = "Elevated SATIS Auto Deck, Platform 1 West exit",
            destination = "Wagle Estate / Passport Office / Road No. 22",
            viaPoints = listOf("Gokhale Road", "Teen Hath Naka", "Wagle Police Stn"),
            distanceKm = 3.6,
            approxMinutes = 14,
            referenceIndicativeFare = 30.0,
            standLatitude = 19.1860,
            standLongitude = 72.9755
        ),
        StationStandRoute(
            id = "c_tna_w_ghodbunder",
            stationName = "Thane",
            stationCode = "TNA",
            line = RailwayLine.CENTRAL,
            side = StationSide.WEST,
            standName = "Thane West Viviana Stand",
            standLandmark = "Outside Station Ticket Windows, Shivaji Path",
            destination = "Viviana Mall / Jupiter Hospital / Ghodbunder Road",
            viaPoints = listOf("Meenatai Thackeray Chowk", "Cadbury Junction", "Majiwada"),
            distanceKm = 5.2,
            approxMinutes = 18,
            referenceIndicativeFare = 40.0,
            standLatitude = 19.1870,
            standLongitude = 72.9740
        ),

        // ==================== HARBOUR LINE ====================
        // Chembur
        StationStandRoute(
            id = "h_chm_e_diamond",
            stationName = "Chembur",
            stationCode = "CMBR",
            line = RailwayLine.HARBOUR,
            side = StationSide.EAST,
            standName = "Chembur East Station Stand",
            standLandmark = "Outside Platform 1 East, Near Monorail Station",
            destination = "Diamond Garden / Ambedkar Garden / Golf Club",
            viaPoints = listOf("Chembur Naka", "Fine Arts Society"),
            distanceKm = 1.8,
            approxMinutes = 7,
            referenceIndicativeFare = 20.0,
            standLatitude = 19.0620,
            standLongitude = 72.8995
        ),

        // Vashi
        StationStandRoute(
            id = "h_vsh_apmc",
            stationName = "Vashi",
            stationCode = "VSH",
            line = RailwayLine.HARBOUR,
            side = StationSide.WEST,
            standName = "Vashi Station Plaza Auto Stand",
            standLandmark = "Outside Vashi Station Forecourt, Sector 30A",
            destination = "APMC Market / Grain Market / Palm Beach Road",
            viaPoints = listOf("Inorbit Mall Vashi", "Turbhe Naka"),
            distanceKm = 3.2,
            approxMinutes = 11,
            referenceIndicativeFare = 30.0,
            standLatitude = 19.0655,
            standLongitude = 72.9985
        )
    )

    const val UNAVAILABLE_LOCATION_MESSAGE =
        "Location coordinates or route timing data are unavailable for this stand."

    fun getAllUniqueStations(line: RailwayLine = RailwayLine.ALL): List<String> {
        return getRoutesByLine(line).map { it.stationName }.distinct()
    }

    fun getRoutesForStation(stationName: String): List<StationStandRoute> {
        return allStationRoutes.filter { it.stationName.equals(stationName, ignoreCase = true) }
    }

    fun getRoutesByLine(line: RailwayLine): List<StationStandRoute> {
        return if (line == RailwayLine.ALL) allStationRoutes else allStationRoutes.filter { it.line == line }
    }

    fun filterBySide(
        routes: List<StationStandRoute>,
        side: StationSide?
    ): List<StationStandRoute> {
        return if (side == null) routes else routes.filter { it.side == side }
    }

    fun searchByStationName(
        stationQuery: String,
        line: RailwayLine = RailwayLine.ALL,
        side: StationSide? = null
    ): List<StationStandRoute> {
        val base = filterBySide(getRoutesByLine(line), side)
        if (stationQuery.isBlank()) return base
        val q = stationQuery.trim().lowercase()
        return base.filter { route ->
            route.stationName.lowercase().contains(q) ||
                route.stationCode.lowercase().contains(q)
        }
    }

    fun searchByDestination(
        destinationQuery: String,
        line: RailwayLine = RailwayLine.ALL,
        side: StationSide? = null
    ): List<StationStandRoute> {
        val base = filterBySide(getRoutesByLine(line), side)
        if (destinationQuery.isBlank()) return base
        val q = destinationQuery.trim().lowercase()
        return base.filter { route ->
            route.destination.lowercase().contains(q) ||
                route.viaPoints.any { it.lowercase().contains(q) } ||
                route.standLandmark.lowercase().contains(q)
        }
    }

    fun filterDirectory(
        stationQuery: String = "",
        destinationQuery: String = "",
        line: RailwayLine = RailwayLine.ALL,
        side: StationSide? = null,
        favoritesOnly: Boolean = false,
        favoriteIds: Set<String> = emptySet()
    ): List<StationStandRoute> {
        val bySide = filterBySide(getRoutesByLine(line), side)
        val trimmedStation = stationQuery.trim().lowercase()
        val trimmedDest = destinationQuery.trim().lowercase()

        return bySide.filter { route ->
            val matchesStation = trimmedStation.isEmpty() ||
                route.stationName.lowercase().contains(trimmedStation) ||
                route.stationCode.lowercase().contains(trimmedStation)

            val matchesDest = trimmedDest.isEmpty() ||
                route.destination.lowercase().contains(trimmedDest) ||
                route.standName.lowercase().contains(trimmedDest) ||
                route.standLandmark.lowercase().contains(trimmedDest) ||
                route.viaPoints.any { it.lowercase().contains(trimmedDest) }

            val matchesFavorite = !favoritesOnly || favoriteIds.contains(route.id)

            matchesStation && matchesDest && matchesFavorite
        }
    }

    fun searchRoutes(query: String, line: RailwayLine = RailwayLine.ALL): List<StationStandRoute> {
        val lineList = getRoutesByLine(line)
        if (query.isBlank()) return lineList
        val q = query.trim().lowercase()
        return lineList.filter { route ->
            route.stationName.lowercase().contains(q) ||
                route.stationCode.lowercase().contains(q) ||
                route.destination.lowercase().contains(q) ||
                route.standName.lowercase().contains(q) ||
                route.standLandmark.lowercase().contains(q) ||
                route.viaPoints.any { it.lowercase().contains(q) }
        }
    }
}
