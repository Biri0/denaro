#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVICE="${ANDROID_SERIAL:-}"
REFERENCE_DATE="$(date +%F)"
COMPOSE_ONLY=false

while (($#)); do
    case "$1" in
        --device)
            DEVICE="${2:?Missing serial after --device}"
            shift 2
            ;;
        --date)
            REFERENCE_DATE="${2:?Missing YYYY-MM-DD after --date}"
            shift 2
            ;;
        --compose-only)
            COMPOSE_ONLY=true
            shift
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 2
            ;;
    esac
done

for command in magick fc-match; do
    command -v "$command" >/dev/null || {
        echo "Missing required command: $command" >&2
        exit 1
    }
done

if [[ "$COMPOSE_ONLY" != true ]]; then
    for command in adb android; do
        command -v "$command" >/dev/null || {
            echo "Missing required command: $command" >&2
            exit 1
        }
    done
fi

if [[ "$COMPOSE_ONLY" != true && -z "$DEVICE" ]]; then
    mapfile -t DEVICES < <(adb devices -l | awk '$2 == "device" && $1 !~ /^emulator-/ { print $1 }')
    if ((${#DEVICES[@]} != 1)); then
        echo "Connect exactly one physical Android device or pass --device SERIAL." >&2
        exit 1
    fi
    DEVICE="${DEVICES[0]}"
fi

if [[ "$COMPOSE_ONLY" != true ]] && adb -s "$DEVICE" shell getprop ro.kernel.qemu | tr -d '\r' | grep -q '^1$'; then
    echo "Refusing to capture marketing assets from an emulator." >&2
    exit 1
fi

RAW_DIR="$PROJECT_DIR/build/marketing-assets/raw"
README_DIR="$PROJECT_DIR/docs/assets/readme"
PLAY_DIR="$PROJECT_DIR/app/src/main/play/listings"
APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
COMPONENT="it.rfmariano.denaro/.debug.MarketingCaptureActivity"
mkdir -p \
    "$RAW_DIR" \
    "$README_DIR" \
    "$PLAY_DIR/en-US/graphics/icon" \
    "$PLAY_DIR/en-US/graphics/phone-screenshots" \
    "$PLAY_DIR/it-IT/graphics/icon" \
    "$PLAY_DIR/it-IT/graphics/phone-screenshots"

if [[ "$COMPOSE_ONLY" != true ]]; then
    "$PROJECT_DIR/gradlew" :app:assembleDebug
    android run --debug --device="$DEVICE" --apks="$APK"
    adb -s "$DEVICE" shell am clear-debug-app
fi

expected_text() {
    local locale="$1"
    local scenario="$2"
    case "$locale:$scenario" in
        en:home) echo "Balances" ;;
        it:home) echo "Saldi" ;;
        en:accounts) echo "Everyday" ;;
        it:accounts) echo "Conto quotidiano" ;;
        en:account_detail) echo "Current balance" ;;
        it:account_detail) echo "Saldo attuale" ;;
        en:activity) echo "Groceries" ;;
        it:activity) echo "Spesa" ;;
        en:debts|it:debts) echo "Alex" ;;
        en:categories) echo "Home" ;;
        it:categories) echo "Casa" ;;
    esac
}

capture() {
    local locale="$1"
    local theme="$2"
    local scenario="$3"
    local output="$RAW_DIR/${locale}-${scenario}-${theme}.png"
    local expected
    expected="$(expected_text "$locale" "$scenario")"

    adb -s "$DEVICE" shell am force-stop it.rfmariano.denaro
    adb -s "$DEVICE" shell am start -W -n "$COMPONENT" \
        --es locale "$locale" \
        --es theme "$theme" \
        --es scenario "$scenario" \
        --es reference_date "$REFERENCE_DATE" >/dev/null

    local ready=false
    for _ in {1..30}; do
        if android layout --device="$DEVICE" | grep -Fq "$expected"; then
            ready=true
            break
        fi
        sleep 0.5
    done
    if [[ "$ready" != true ]]; then
        echo "Timed out waiting for $locale/$theme/$scenario ($expected)." >&2
        exit 1
    fi
    android screen capture --device="$DEVICE" -o "$output"
}

if [[ "$COMPOSE_ONLY" != true ]]; then
    for locale in en it; do
        for theme in light dark; do
            for scenario in home accounts account_detail activity debts categories; do
                capture "$locale" "$theme" "$scenario"
            done
        done
    done
fi

split_diagonal() {
    local light="$1"
    local dark="$2"
    local output="$3"
    local width height scale_width scale_height mask
    width="$(magick identify -format '%w' "$light")"
    height="$(magick identify -format '%h' "$light")"
    scale_width=$((width * 4))
    scale_height=$((height * 4))
    mask="$RAW_DIR/diagonal-mask-${width}x${height}.png"
    magick -size "${scale_width}x${scale_height}" xc:black \
        -fill white -draw "polygon 0,0 ${scale_width},0 0,${scale_height}" \
        -resize "${width}x${height}" "$mask"
    magick "$dark" "$light" "$mask" -compose over -composite "PNG24:$output"
}

split_diagonal "$RAW_DIR/en-home-light.png" "$RAW_DIR/en-home-dark.png" "$README_DIR/home.png"
split_diagonal "$RAW_DIR/en-accounts-light.png" "$RAW_DIR/en-account_detail-dark.png" "$README_DIR/accounts.png"
split_diagonal "$RAW_DIR/en-activity-light.png" "$RAW_DIR/en-activity-dark.png" "$README_DIR/activity.png"
split_diagonal "$RAW_DIR/en-debts-light.png" "$RAW_DIR/en-debts-dark.png" "$README_DIR/debts.png"

make_store_card() {
    local locale="$1"
    local listing_locale="$2"
    local order="$3"
    local key="$4"
    local light_scenario="$5"
    local dark_scenario="$6"
    local title="$7"
    local split="$RAW_DIR/${locale}-${key}-split.png"
    local output="$PLAY_DIR/$listing_locale/graphics/phone-screenshots/${order}-${key}.png"
    local font
    font="$(fc-match -f '%{file}' 'DejaVu Sans:style=Bold' | head -n 1)"
    split_diagonal \
        "$RAW_DIR/${locale}-${light_scenario}-light.png" \
        "$RAW_DIR/${locale}-${dark_scenario}-dark.png" \
        "$split"
    magick -size 1080x1920 xc:'#EDF7F1' \
        -fill '#D9EDE3' -draw 'polygon 0,1920 1080,1180 1080,1920' \
        -fill '#006C4C' -font "$font" -pointsize 64 -gravity north \
        -annotate +0+92 "$title" \
        \( "$split" -resize '760x1480>' -bordercolor '#FFFFFF' -border 4 \) \
        -gravity south -geometry +0+62 -composite -alpha off "PNG24:$output"
}

make_store_card en en-US 1 dashboard home home "Your month at a glance"
make_store_card en en-US 2 accounts account_detail account_detail "Every account, one clear view"
make_store_card en en-US 3 activity activity activity "Find every movement fast"
make_store_card en en-US 4 debts debts debts "Keep debts and repayments clear"
make_store_card en en-US 5 categories categories categories "Organize money your way"
make_store_card it it-IT 1 dashboard home home "Il tuo mese a colpo d'occhio"
make_store_card it it-IT 2 accounts account_detail account_detail "Tutti i conti, in una vista chiara"
make_store_card it it-IT 3 activity activity activity "Trova subito ogni movimento"
make_store_card it it-IT 4 debts debts debts "Debiti e rimborsi sempre chiari"
make_store_card it it-IT 5 categories categories categories "Organizza il denaro a modo tuo"

for locale in en-US it-IT; do
    cp "$PROJECT_DIR/app/src/main/ic_launcher-playstore.png" \
        "$PLAY_DIR/$locale/graphics/icon/icon.png"
done

magick identify \
    "$README_DIR"/*.png \
    "$PLAY_DIR"/*/graphics/icon/*.png \
    "$PLAY_DIR"/*/graphics/phone-screenshots/*.png
echo "Marketing assets updated in docs/assets/readme and app/src/main/play."
