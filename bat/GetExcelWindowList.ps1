[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Function to get the details of all open Excel windows
function Get-ExcelWorkbooks {
    $workbooks = @()
    try {
        $excel = [Runtime.InteropServices.Marshal]::GetActiveObject("Excel.Application")
        foreach ($workbook in $excel.Workbooks) {
            $workbooks += [PSCustomObject]@{
                WorkbookName = $workbook.Name
                ActiveSheetName = $workbook.ActiveSheet.Name
            }
        }
    } catch {}
    return $workbooks
}

# Call the function and capture the details of the windows
$excelWorkbooks = Get-ExcelWorkbooks

# Display the details of the Excel windows
if( $excelWorkbooks.Count -eq $null ) {
    [Console]::WriteLine("1")
} else {
    [Console]::WriteLine("{0}", $excelWorkbooks.Count)
}
foreach ($entry  in $excelWorkbooks) {
    [Console]::WriteLine("{0}`t{1}", $entry.WorkbookName, $entry.ActiveSheetName)
}

