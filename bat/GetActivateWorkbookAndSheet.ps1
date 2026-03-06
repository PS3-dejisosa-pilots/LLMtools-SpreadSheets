[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Function to get the names of the active workbook and its active sheet
function Get-ActiveWorkbookAndSheet {
    # Try to get the existing Excel COM object
    try {
        $excel = [Runtime.InteropServices.Marshal]::GetActiveObject("Excel.Application")
    } catch {
        [Console]::WriteLine("0")
        exit 1
    }

    # Get the active workbook and sheet
    try {
        $activeWorkbook = $excel.ActiveWorkbook
        $activeSheet = $activeWorkbook.ActiveSheet
        $workbookName = $activeWorkbook.Name
        $sheetName = $activeSheet.Name
        # Write-Output "1"
        # Write-Output "$workbookName,$sheetName"
        [Console]::WriteLine("1")
        [Console]::WriteLine("$workbookName,$sheetName")
    } catch {
        [Console]::WriteLine("0")
        exit 1
    }
}

# Call the function
Get-ActiveWorkbookAndSheet
