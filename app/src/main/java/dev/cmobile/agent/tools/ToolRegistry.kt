package dev.cmobile.agent.tools

/** Every tool the agent can call, in the order they're advertised to the model. */
object ToolRegistry {

    val all: List<AgentTool> = listOf(
        // Device
        GetAndroidVersion,
        GetBuildVersion,
        GetDeviceName,
        GetDeviceModel,
        GetSystemStats,
        GetTime,
        ListInstalledApps,
        // Connectivity
        GetConnectionMethod,
        IsHotspotRunning,
        // Power
        GetBatteryInfo,
        // Shell
        RunCmd,
        GetShizukuStatus,
        ConnectShizuku,
        RunShizukuCmd,
        // Web
        WebSearch,
        WebFetch,
        // Notifications
        CheckNotificationPermission,
        RequestNotificationPermission,
        SendNotification,
        // Self
        ReadIdentity,
        WriteIdentity,
        ReadMemory,
        AppendMemory,
        WriteMemory,
        SetAgentName,
        // Scheduling
        CreateCron,
        ListCrons,
        DeleteCron,
        SetCronEnabled,
    )

    private val byName: Map<String, AgentTool> = all.associateBy { it.name }

    fun find(name: String): AgentTool? = byName[name]

    val specs get() = all.map { it.toSpec() }
}
