# Copy selected area cell data for EXCEL


# Parse commandline args
param(
    [String]$workbookName = $null,
    [String]$sheetName = $null
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Try to get the existing Excel COM object
try {
    $excel = [Runtime.InteropServices.Marshal]::GetActiveObject("Excel.Application")
} catch {
    [Console]::WriteLine("0")
    exit 1
}

try {
    # Get target workbook and sheet
    # If no params, select active EXCEL file
    $selection = $null

    if ([System.String]::IsNullOrEmpty($workbookName) -or [System.String]::IsNullOrEmpty($sheetName)) {
        $tgtWorkbook = $excel.ActiveWorkbook
        $tgtSheet = $activeWorkbook.ActiveSheet
        $selection = $excel.Selection

    } else {
        $tgtWorkbook = $excel.Workbooks | Where-Object { $_.Name -eq $workbookName }

        if ($null -eq $tgtWorkbook) {
            [Console]::WriteLine("0")
            exit 1
        }

        # Active target workbook
        $tgtWorkbook.Activate()

        $tgtSheet = $tgtWorkbook.Sheets.Item($sheetName)
        $selection = $tgtSheet.Application.Selection
    }

    if ($null -eq $selection) {
        [Console]::WriteLine("0")
        exit 1
    }

    # Get the selected range's address
    $selectionAddress = $selection.Address

    # Output the selectedArea data
    [Console]::WriteLine("$($selection.Rows.Count)")
    foreach ($row in $selection.Rows) {
        $rowData = @()
        foreach ($cell in $row.Columns) {
            if( $cell.Value2 -eq $null ){
                $rowData += "<<__NULL__>>"
            } else {
                $rowData += $cell.Value2.ToString().replace("`n", "<<__RETURN__>>").replace("`r", "<<__RETURN__>>")
            }
        }

        # debug print
        [Console]::WriteLine("{0}", $rowData -join "`t")
    }

} catch {
    [Console]::WriteLine("0")
    exit 1
}
