param(
    [Parameter(Mandatory = $true)]
    [string]$workbookName
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

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

# List all sheet names in the specified workbook
$sheets = $workbook.Sheets
$sheetsList = @()

foreach ($sheet in $sheets) {
    $sheetsList += $sheet.Name
}

# Output the list of sheet names
[Console]::WriteLine("$($sheetsList.Count)")
foreach ($sheetName in $sheetsList) {
    [Console]::WriteLine($sheetName)
}
