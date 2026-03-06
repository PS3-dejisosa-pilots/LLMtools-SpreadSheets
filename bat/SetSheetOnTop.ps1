param(
    [Parameter(Mandatory = $true)]
    [string]$workbookName,
    [Parameter(Mandatory = $true)]
    [string]$sheetName
)

# Function to bring a window to the foreground using its handle
function Set-ForegroundWindow {
    param(
        [Parameter(Mandatory = $true)]
        [System.IntPtr]$hWnd
    )

    if( -not [System.Management.Automation.PSTypeName]'User32'.Type ){
        Add-Type @"
            using System;
            using System.Runtime.InteropServices;
            public class User32 {
                [DllImport("user32.dll")]
                public static extern bool SetForegroundWindow(IntPtr hWnd);

                [DllImport("user32.dll")]
                public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

                public const int SW_RESTORE = 9;
            }
"@
    }
    [User32]::ShowWindow($hWnd, [User32]::SW_RESTORE)
    [User32]::SetForegroundWindow($hWnd)
}

# Try to get the existing Excel COM object
try {
    $excel = [Runtime.InteropServices.Marshal]::GetActiveObject("Excel.Application")
} catch {
    [Console]::WriteLine("0")
    exit 1
}

$workbook = $null

# Iterate through each workbook to find the specified one
foreach ($wb in $excel.Workbooks) {
    if ($wb.Name -eq $workbookName) {
        $workbook = $wb
        break
    }
}

if ($workbook -eq $null) {
    [Console]::WriteLine("0")
    exit 1
}

$sheet = $null

# Find the specified sheet in the workbook
foreach ($sh in $workbook.Sheets) {
    if ($sh.Name -eq $sheetName) {
        $sheet = $sh
        break
    }
}

if ($sheet -eq $null) {
    [Console]::WriteLine("0")
    exit 1
}

# Activate the workbook and sheet
$sheet.Activate()
$workbook.Windows.Item(1).Activate()

[Console]::WriteLine("2")

# Bring Excel to the front
$hWnd = [System.IntPtr]::new($excel.Hwnd)
Set-ForegroundWindow -hWnd $hWnd

