
fun generateIndexHtml(jsonFiles: List<String>): String {
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Saved Forms</title>
            <style>
                body {
                    font-family: Arial, sans-serif;
                    max-width: 800px;
                    margin: 0 auto;
                    padding: 20px;
                    background-color: #f5f5f5;
                }
                .container {
                    background: white;
                    padding: 30px;
                    border-radius: 8px;
                    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                }
                h1 {
                    color: #333;
                    text-align: center;
                    margin-bottom: 30px;
                }
                .file-list {
                    list-style: none;
                    padding: 0;
                }
                .file-item {
                    margin: 10px 0;
                    padding: 15px;
                    background: #f8f9fa;
                    border: 1px solid #e9ecef;
                    border-radius: 4px;
                    cursor: pointer;
                    transition: background-color 0.2s;
                }
                .file-item:hover {
                    background: #e9ecef;
                }
                .file-item:active {
                    background: #dee2e6;
                }
                .no-files {
                    text-align: center;
                    color: #666;
                    font-style: italic;
                    padding: 40px;
                }
                .loading {
                    display: none;
                    text-align: center;
                    color: #007bff;
                    margin-top: 20px;
                }
                .error {
                    color: #dc3545;
                    text-align: center;
                    margin-top: 20px;
                    padding: 10px;
                    background: #f8d7da;
                    border: 1px solid #f5c6cb;
                    border-radius: 4px;
                }
                #renderForm {
                    display: none;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>Saved Forms</h1>
                ${if (jsonFiles.isEmpty()) {
        "<div class=\"no-files\">No forms found in the forms directory.</div>"
    } else {
        "<ul class=\"file-list\">" +
                jsonFiles.joinToString("") { filename ->
                    "<li class=\"file-item\" onclick=\"loadAndRenderForm('$filename')\">$filename</li>"
                } +
                "</ul>"
    }}
                <div class="loading" id="loading">Loading form data...</div>
                <div class="error" id="error" style="display: none;"></div>
            </div>

            <!-- Hidden form for POST to /render -->
            <form id="renderForm" action="/render" method="POST" target="_self">
                <input type="hidden" name="jsonData" id="jsonDataInput">
            </form>

            <script>
                async function loadAndRenderForm(filename) {
                    const loadingEl = document.getElementById('loading');
                    const errorEl = document.getElementById('error');
                    const renderForm = document.getElementById('renderForm');
                    const jsonDataInput = document.getElementById('jsonDataInput');
                    
                    // Show loading, hide error
                    loadingEl.style.display = 'block';
                    errorEl.style.display = 'none';
                    
                    try {
                        // Fetch the JSON file
                        const response = await fetch('/forms/' + filename);
                        if (!response.ok) {
                            throw new Error('Failed to load form: ' + response.statusText);
                        }
                        
                        const jsonData = await response.text();
                        
                        // Set the JSON data in the hidden input and submit the form
                        jsonDataInput.value = jsonData;
                        renderForm.submit();
                        
                    } catch (error) {
                        console.error('Error:', error);
                        errorEl.textContent = 'Error: ' + error.message;
                        errorEl.style.display = 'block';
                        loadingEl.style.display = 'none';
                    }
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}
