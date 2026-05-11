/**
 * IMEI Country Checker
 * Identifies the country of origin based on IMEI TAC (Type Allocation Code) prefix.
 * The first 2 digits of the TAC are the Reporting Body Identifier (RBI).
 */

// Reporting Body Identifier (RBI) to Country/Organization mapping
// Based on GSMA allocations
const RBI_DATABASE = {
    "01": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "02": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "03": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "04": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "05": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "06": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "07": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "08": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "09": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "10": { country: "Finland", body: "FICORA", flag: "\ud83c\uddeb\ud83c\uddee" },
    "30": { country: "France", body: "GGRF", flag: "\ud83c\uddeb\ud83c\uddf7" },
    "31": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "32": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "33": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "34": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "35": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "36": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "37": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "38": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "39": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "40": { country: "Germany", body: "BNetzA", flag: "\ud83c\udde9\ud83c\uddea" },
    "41": { country: "Germany", body: "BNetzA", flag: "\ud83c\udde9\ud83c\uddea" },
    "42": { country: "Germany", body: "BNetzA", flag: "\ud83c\udde9\ud83c\uddea" },
    "43": { country: "Germany", body: "BNetzA", flag: "\ud83c\udde9\ud83c\uddea" },
    "44": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "45": { country: "Japan", body: "JATE/TEC", flag: "\ud83c\uddef\ud83c\uddf5" },
    "46": { country: "Japan", body: "JATE/TEC", flag: "\ud83c\uddef\ud83c\uddf5" },
    "47": { country: "United States", body: "PTCRB", flag: "\ud83c\uddfa\ud83c\uddf8" },
    "48": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "49": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "50": { country: "South Korea", body: "MSIP/KCC", flag: "\ud83c\uddf0\ud83c\uddf7" },
    "51": { country: "South Korea", body: "MSIP/KCC", flag: "\ud83c\uddf0\ud83c\uddf7" },
    "52": { country: "South Korea", body: "MSIP/KCC", flag: "\ud83c\uddf0\ud83c\uddf7" },
    "53": { country: "Ireland", body: "ComReg", flag: "\ud83c\uddee\ud83c\uddea" },
    "54": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "55": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "56": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "57": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "58": { country: "India", body: "TEC", flag: "\ud83c\uddee\ud83c\uddf3" },
    "59": { country: "United Kingdom", body: "BABT", flag: "\ud83c\uddec\ud83c\udde7" },
    "60": { country: "Canada", body: "ISED", flag: "\ud83c\udde8\ud83c\udde6" },
    "61": { country: "United States", body: "PTCRB", flag: "\ud83c\uddfa\ud83c\uddf8" },
    "62": { country: "Brazil", body: "Anatel", flag: "\ud83c\udde7\ud83c\uddf7" },
    "63": { country: "Brazil", body: "Anatel", flag: "\ud83c\udde7\ud83c\uddf7" },
    "64": { country: "Mexico", body: "IFT", flag: "\ud83c\uddf2\ud83c\uddfd" },
    "65": { country: "South Africa", body: "ICASA", flag: "\ud83c\uddff\ud83c\udde6" },
    "66": { country: "Thailand", body: "NBTC", flag: "\ud83c\uddf9\ud83c\udded" },
    "67": { country: "Colombia", body: "CRC", flag: "\ud83c\udde8\ud83c\uddf4" },
    "68": { country: "Indonesia", body: "SDPPI", flag: "\ud83c\uddee\ud83c\udde9" },
    "69": { country: "Turkey", body: "BTK", flag: "\ud83c\uddf9\ud83c\uddf7" },
    "70": { country: "Spain", body: "CNMC", flag: "\ud83c\uddea\ud83c\uddf8" },
    "71": { country: "Egypt", body: "NTRA", flag: "\ud83c\uddea\ud83c\uddec" },
    "72": { country: "Morocco", body: "ANRT", flag: "\ud83c\uddf2\ud83c\udde6" },
    "73": { country: "Nigeria", body: "NCC", flag: "\ud83c\uddf3\ud83c\uddec" },
    "74": { country: "Saudi Arabia", body: "CITC", flag: "\ud83c\uddf8\ud83c\udde6" },
    "75": { country: "Argentina", body: "ENACOM", flag: "\ud83c\udde6\ud83c\uddf7" },
    "76": { country: "Italy", body: "AGCOM", flag: "\ud83c\uddee\ud83c\uddf9" },
    "77": { country: "Russia", body: "Rossvyaz", flag: "\ud83c\uddf7\ud83c\uddfa" },
    "78": { country: "Poland", body: "UKE", flag: "\ud83c\uddf5\ud83c\uddf1" },
    "79": { country: "Australia", body: "ACMA", flag: "\ud83c\udde6\ud83c\uddfa" },
    "80": { country: "China", body: "CMIIT", flag: "\ud83c\udde8\ud83c\uddf3" },
    "81": { country: "China", body: "CMIIT", flag: "\ud83c\udde8\ud83c\uddf3" },
    "82": { country: "China", body: "CMIIT", flag: "\ud83c\udde8\ud83c\uddf3" },
    "83": { country: "China", body: "CMIIT", flag: "\ud83c\udde8\ud83c\uddf3" },
    "84": { country: "China", body: "CMIIT", flag: "\ud83c\udde8\ud83c\uddf3" },
    "85": { country: "China", body: "CMIIT", flag: "\ud83c\udde8\ud83c\uddf3" },
    "86": { country: "China", body: "TAF", flag: "\ud83c\udde8\ud83c\uddf3" },
    "87": { country: "China", body: "TAF", flag: "\ud83c\udde8\ud83c\uddf3" },
    "88": { country: "China", body: "TAF", flag: "\ud83c\udde8\ud83c\uddf3" },
    "89": { country: "China", body: "TAF", flag: "\ud83c\udde8\ud83c\uddf3" },
    "90": { country: "Taiwan", body: "NCC Taiwan", flag: "\ud83c\uddf9\ud83c\uddfc" },
    "91": { country: "India", body: "MSAI/TEC", flag: "\ud83c\uddee\ud83c\uddf3" },
    "92": { country: "Pakistan", body: "PTA", flag: "\ud83c\uddf5\ud83c\uddf0" },
    "93": { country: "Vietnam", body: "VNTA", flag: "\ud83c\uddfb\ud83c\uddf3" },
    "94": { country: "Malaysia", body: "MCMC", flag: "\ud83c\uddf2\ud83c\uddfe" },
    "95": { country: "Myanmar", body: "PTD", flag: "\ud83c\uddf2\ud83c\uddf2" },
    "96": { country: "Philippines", body: "NTC", flag: "\ud83c\uddf5\ud83c\udded" },
    "97": { country: "Bangladesh", body: "BTRC", flag: "\ud83c\udde7\ud83c\udde9" },
    "98": { country: "United States", body: "CTIA/PTCRB", flag: "\ud83c\uddfa\ud83c\uddf8" },
    "99": { country: "United States", body: "CTIA/PTCRB", flag: "\ud83c\uddfa\ud83c\uddf8" }
};

/**
 * Validate IMEI using Luhn algorithm
 */
function validateIMEI(imei) {
    if (imei.length !== 15) return false;
    if (!/^\d{15}$/.test(imei)) return false;

    let sum = 0;
    for (let i = 0; i < 14; i++) {
        let digit = parseInt(imei[i]);
        if (i % 2 === 1) {
            digit *= 2;
            if (digit > 9) digit -= 9;
        }
        sum += digit;
    }

    const checkDigit = (10 - (sum % 10)) % 10;
    return checkDigit === parseInt(imei[14]);
}

/**
 * Get country information from IMEI
 */
function getCountryFromIMEI(imei) {
    const rbi = imei.substring(0, 2);
    return RBI_DATABASE[rbi] || null;
}

/**
 * Main check function
 */
function checkIMEI() {
    const input = document.getElementById('imei-input');
    const errorEl = document.getElementById('error-msg');
    const resultEl = document.getElementById('result');

    // Clean input
    const imei = input.value.replace(/[\s\-]/g, '');

    // Hide previous results
    errorEl.classList.add('hidden');
    resultEl.classList.add('hidden');

    // Validate input
    if (!imei) {
        showError('Please enter an IMEI number.');
        return;
    }

    if (!/^\d+$/.test(imei)) {
        showError('IMEI must contain only digits (0-9).');
        return;
    }

    if (imei.length !== 15) {
        showError(`IMEI must be exactly 15 digits. You entered ${imei.length} digits.`);
        return;
    }

    // Get country info
    const countryInfo = getCountryFromIMEI(imei);
    const isValid = validateIMEI(imei);
    const tac = imei.substring(0, 8);
    const rbi = imei.substring(0, 2);

    if (!countryInfo) {
        showError(`Unknown Reporting Body Identifier: ${rbi}. Unable to determine country.`);
        return;
    }

    // Display results
    document.getElementById('country-flag').textContent = countryInfo.flag;
    document.getElementById('country-name').textContent = countryInfo.country;
    document.getElementById('result-imei').textContent = formatIMEI(imei);
    document.getElementById('result-tac').textContent = tac;
    document.getElementById('result-rbi').textContent = `${rbi} - ${countryInfo.body}`;
    document.getElementById('result-country').textContent = countryInfo.country;
    document.getElementById('result-valid').textContent = isValid ? '\u2705 Valid' : '\u274c Invalid checksum';
    document.getElementById('result-valid').style.color = isValid ? '#4ade80' : '#ff6b7a';

    resultEl.classList.remove('hidden');
}

/**
 * Format IMEI for display
 */
function formatIMEI(imei) {
    return `${imei.substring(0, 2)}-${imei.substring(2, 8)}-${imei.substring(8, 14)}-${imei[14]}`;
}

/**
 * Show error message
 */
function showError(message) {
    const errorEl = document.getElementById('error-msg');
    errorEl.textContent = message;
    errorEl.classList.remove('hidden');
}

// Allow Enter key to trigger check
document.getElementById('imei-input').addEventListener('keypress', function(e) {
    if (e.key === 'Enter') {
        checkIMEI();
    }
});

// Only allow digits in input
document.getElementById('imei-input').addEventListener('input', function(e) {
    this.value = this.value.replace(/[^\d]/g, '');
});
