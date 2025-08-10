' Script per estrarre il codice sorgente (.kt) e i file XML in un unico file di testo.

Option Explicit

Dim fso, outputFile, currentFolder
Const ForReading = 1

' Crea gli oggetti necessari
Set fso = CreateObject("Scripting.FileSystemObject")
Set outputFile = fso.CreateTextFile("LifeLog_Codice_Completo.txt", True) ' True = sovrascrivi se esiste

' Ottiene la cartella corrente (dove si trova lo script)
Set currentFolder = fso.GetFolder(".")

' Avvia il processo ricorsivo
WScript.Echo "Inizio estrazione del codice..."
ProcessFolder(currentFolder)

' Chiude il file di output e notifica l'utente
outputFile.Close
WScript.Echo "Estrazione completata con successo!"
WScript.Echo "Il codice è stato salvato in: " & fso.GetAbsolutePathName("LifeLog_Codice_Completo.txt")

' Funzione ricorsiva per processare le cartelle
Sub ProcessFolder(folder)
    Dim file, subfolder, fileExtension, fileContent

    ' Processa i file nella cartella corrente
    For Each file In folder.Files
        fileExtension = LCase(fso.GetExtensionName(file.Name))

        ' Controlla se l'estensione è .kt o .xml
        If fileExtension = "kt" Or fileExtension = "xml" Then
            outputFile.WriteLine("--- INIZIO FILE: " & file.Path & " ---")
            outputFile.WriteLine("")

            ' Legge il contenuto del file e lo scrive nell'output
            Dim sourceFile
            Set sourceFile = file.OpenAsTextStream(ForReading)
            If Not sourceFile.AtEndOfStream Then
                fileContent = sourceFile.ReadAll()
                outputFile.WriteLine(fileContent)
            End If
            sourceFile.Close

            outputFile.WriteLine("")
            outputFile.WriteLine("--- FINE FILE: " & file.Path & " ---")
            outputFile.WriteLine("")
            outputFile.WriteLine("")
        End If
    Next

    ' Chiama se stessa per ogni sottocartella
    For Each subfolder In folder.SubFolders
        ProcessFolder(subfolder)
    Next
End Sub