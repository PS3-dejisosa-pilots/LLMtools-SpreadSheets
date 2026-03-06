function Set-ExcelSelectionFromStdin {
    param (
        [Parameter(Mandatory = $true)]
        [int]$rowCount,
        [Parameter(Mandatory = $true)]
        [string[]]$inputLines
    )

    # Try to get the existing Excel COM object
    try {
        $excel = [Runtime.InteropServices.Marshal]::GetActiveObject("Excel.Application")
    } catch {
        [Console]::WriteLine("0")
        exit 1
    }

    # Get the active workbook, sheet, and selection
    try {
        $activeWorkbook = $excel.ActiveWorkbook
        $activeSheet = $activeWorkbook.ActiveSheet
        $selection = $excel.Selection

        if ($null -eq $selection) {
            [Console]::WriteLine("0")
            exit 1
        }

        # Ensure the selection can contain the input data
        if ($selection.Rows.Count -lt $rowCount) {
            [Console]::WriteLine("0")
            exit 1
        }

        # Convert input lines into data array
        $dataArray = @()
        foreach ($line in $inputLines) {
            $columns = $line -split "`t"
            for ($i = 0; $i -lt $columns.Length; $i++) {
                if ($columns[$i] -eq "<<__NULL__>>") {
                    $columns[$i] = $null
                } elseif ($columns[$i] -match "<<__RETURN__>>") {
                    $columns[$i] = $columns[$i] -replace "<<__RETURN__>>", "`n"
                }
            }
            $dataArray += ,$columns
        }

        # Set the data to the selected range
        for ($rowIndex = 0; $rowIndex -lt $rowCount; $rowIndex++) {
            $currentRow = $selection.Rows.Item($rowIndex + 1)
            $rowData = $dataArray[$rowIndex]
            for ($colIndex = 0; $colIndex -lt $rowData.Length; $colIndex++) {
                $currentRow.Columns.Item($colIndex + 1).Value2 = $rowData[$colIndex]
            }
        }
    } catch {
        [Console]::WriteLine("0")
        exit 1
    }
}

# read the number of lines from standard input
$c = [Console]::ReadLine()
$linecount = [int]($c)

# Read input from standard input
$inputLines = @()
for ($i = 0; $i -lt $linecount; $i++) {
    $inputLines += [Console]::In.ReadLine()
}

$rowCount = $inputLines.Length

if ($rowCount -gt 0) {
    Set-ExcelSelectionFromStdin -rowCount $rowCount -inputLines $inputLines
    [Console]::WriteLine("1")
    [Console]::WriteLine("true")
} else {
    [Console]::WriteLine("0")
    exit 1
}

