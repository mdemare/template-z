let dataClasses = {};
let currentRootClass = '';

function parseDataClasses() {
    const input = document.getElementById('dataClassInput').value;
    dataClasses = {};

    // Parse data class definitions
    const classRegex = /data class (\w+)\s*\(\s*([\s\S]*?)\s*\)/g;
    let match;

    while ((match = classRegex.exec(input)) !== null) {
        const className = match[1];
        const fieldsStr = match[2];

        const fields = parseFields(fieldsStr);
        dataClasses[className] = fields;
    }

    if (Object.keys(dataClasses).length === 0) {
        alert('No valid data classes found. Please check your syntax.');
        return;
    }

    // Use the first data class as root, or let user choose
    currentRootClass = Object.keys(dataClasses)[0];
    generateForm();
}

function parseFields(fieldsStr) {
    const fields = [];
    const fieldRegex = /val\s+(\w+):\s*(.*?)(?=,\s*val|\s*$)/g;
    let match;

    while ((match = fieldRegex.exec(fieldsStr)) !== null) {
        const fieldName = match[1];
        const fieldType = match[2].trim();

        fields.push({
            name: fieldName,
            type: parseType(fieldType)
        });
    }

    return fields;
}

function parseType(typeStr) {
    typeStr = typeStr.trim();

    if (typeStr === 'String') {
        return { kind: 'string' };
    }

    if (typeStr.startsWith('Array<') && typeStr.endsWith('>')) {
        const innerType = typeStr.slice(6, -1);
        return {
            kind: 'array',
            elementType: parseType(innerType)
        };
    }

    // Assume it's a data class
    return {
        kind: 'dataclass',
        className: typeStr
    };
}

function generateForm() {
    const container = document.getElementById('formContainer');
    container.innerHTML = '';

    const rootDiv = document.createElement('div');
    rootDiv.className = 'field-group';

    const title = document.createElement('h4');
    title.textContent = `${currentRootClass} Instance`;
    rootDiv.appendChild(title);

    generateFieldsForClass(rootDiv, currentRootClass, '');
    container.appendChild(rootDiv);

    // Add save button
    const saveBtn = document.createElement('button');
    saveBtn.classList.add('save');
    saveBtn.textContent = '💾 Save';
    saveBtn.onclick = saveData;
    container.appendChild(saveBtn);

    // Add class selector if multiple classes
    if (Object.keys(dataClasses).length > 1) {
        const selector = document.createElement('select');
        selector.style.margin = '10px 5px';
        selector.style.padding = '8px';
        selector.style.borderRadius = '4px';
        selector.style.border = '1px solid #d1d5db';

        Object.keys(dataClasses).forEach(className => {
            const option = document.createElement('option');
            option.value = className;
            option.textContent = className;
            option.selected = className === currentRootClass;
            selector.appendChild(option);
        });

        selector.onchange = (e) => {
            currentRootClass = e.target.value;
            generateForm();
        };

        const selectorLabel = document.createElement('label');
        selectorLabel.textContent = 'Root Class: ';
        container.insertBefore(selectorLabel, container.firstChild);
        container.insertBefore(selector, saveBtn);
    }
}

function generateFieldsForClass(container, className, prefix) {
    if (!dataClasses[className]) {
        console.error(`Class ${className} not found`);
        return;
    }

    dataClasses[className].forEach(field => {
        const fieldPath = prefix ? `${prefix}.${field.name}` : field.name;

        if (field.type.kind === 'string') {
            const label = document.createElement('label');
            label.textContent = field.name;
            container.appendChild(label);

            const input = document.createElement('input');
            input.type = 'text';
            input.dataset.path = fieldPath;
            container.appendChild(input);

        } else if (field.type.kind === 'dataclass') {
            const subDiv = document.createElement('div');
            subDiv.className = 'field-group';
            subDiv.style.marginLeft = '20px';

            const subTitle = document.createElement('h4');
            subTitle.textContent = `${field.name} (${field.type.className})`;
            subDiv.appendChild(subTitle);

            generateFieldsForClass(subDiv, field.type.className, fieldPath);
            container.appendChild(subDiv);

        } else if (field.type.kind === 'array') {
            const arrayDiv = document.createElement('div');
            arrayDiv.className = 'array-section';

            const arrayTitle = document.createElement('h4');
            arrayTitle.textContent = `${field.name} (Array)`;
            arrayDiv.appendChild(arrayTitle);

            const itemsContainer = document.createElement('div');
            itemsContainer.dataset.arrayPath = fieldPath;
            itemsContainer.dataset.elementType = JSON.stringify(field.type.elementType);
            arrayDiv.appendChild(itemsContainer);

            const addBtn = document.createElement('button');
            addBtn.textContent = `➕ Add ${field.type.elementType.className || 'Item'}`;
            addBtn.onclick = () => addArrayItem(itemsContainer, field.type.elementType, fieldPath);
            arrayDiv.appendChild(addBtn);

            container.appendChild(arrayDiv);
        }
    });
}

function addArrayItem(container, elementType, arrayPath) {
    const itemDiv = document.createElement('div');
    itemDiv.className = 'array-item';

    const removeBtn = document.createElement('button');
    removeBtn.className = 'remove-btn';
    removeBtn.textContent = '✕';
    removeBtn.onclick = () => container.removeChild(itemDiv);
    itemDiv.appendChild(removeBtn);

    const index = container.children.length;
    const itemPath = `${arrayPath}[${index}]`;

    if (elementType.kind === 'string') {
        const input = document.createElement('input');
        input.type = 'text';
        input.dataset.path = itemPath;
        input.placeholder = 'Enter value...';
        itemDiv.appendChild(input);
    } else if (elementType.kind === 'dataclass') {
        const title = document.createElement('h5');
        title.textContent = `${elementType.className} #${index + 1}`;
        title.style.margin = '0 0 10px 0';
        itemDiv.appendChild(title);

        generateFieldsForClass(itemDiv, elementType.className, itemPath);
    }

    container.appendChild(itemDiv);
}

async function saveData() {
    const result = collectFormData(currentRootClass, '');
    const jsonOutput = document.getElementById('jsonOutput');

    try {
        // Show loading state
        const saveBtn = document.querySelector('button.save');
        const originalText = saveBtn.textContent;
        saveBtn.textContent = '⏳ Saving...';
        saveBtn.disabled = true;

        const response = await fetch('/save', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(result)
        });

        if (response.ok) {
            const responseData = await response.text();
            jsonOutput.textContent = `✅ Saved successfully!\n\nResponse: ${responseData}\n\nSaved data:\n${JSON.stringify(result, null, 2)}`;
            saveBtn.textContent = '✅ Saved!';

            // Reset button after 2 seconds
            setTimeout(() => {
                saveBtn.textContent = originalText;
                saveBtn.disabled = false;
            }, 2000);
        } else {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

    } catch (error) {
        console.error('Save failed:', error);
        jsonOutput.textContent = `❌ Save failed: ${error.message}\n\nAttempted to save:\n${JSON.stringify(result, null, 2)}`;

        // Reset button
        const saveBtn = document.querySelector('button.save');
        saveBtn.textContent = '❌ Failed';
        saveBtn.disabled = false;

        setTimeout(() => {
            saveBtn.textContent = '💾 Save';
        }, 3000);
    }
}

function collectFormData(className, prefix) {
    const result = {};

    if (!dataClasses[className]) return result;

    dataClasses[className].forEach(field => {
        const fieldPath = prefix ? `${prefix}.${field.name}` : field.name;

        if (field.type.kind === 'string') {
            const input = document.querySelector(`input[data-path="${fieldPath}"]`);
            result[field.name] = input ? input.value : '';

        } else if (field.type.kind === 'dataclass') {
            result[field.name] = collectFormData(field.type.className, fieldPath);

        } else if (field.type.kind === 'array') {
            const arrayContainer = document.querySelector(`div[data-array-path="${fieldPath}"]`);
            const items = [];

            if (arrayContainer) {
                Array.from(arrayContainer.children).forEach((itemDiv, index) => {
                    if (itemDiv.className === 'array-item') {
                        const itemPath = `${fieldPath}[${index}]`;

                        if (field.type.elementType.kind === 'string') {
                            const input = itemDiv.querySelector(`input[data-path="${itemPath}"]`);
                            if (input) items.push(input.value);
                        } else if (field.type.elementType.kind === 'dataclass') {
                            items.push(collectFormData(field.type.elementType.className, itemPath));
                        }
                    }
                });
            }

            result[field.name] = items;
        }
    });

    return result;
}

// Initialize with sample data
parseDataClasses();
