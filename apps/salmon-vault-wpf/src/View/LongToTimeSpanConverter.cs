using System;
using System.Globalization;
using System.Windows.Data;

namespace Salmon.Vault.View;

public class LongToTimeSpanConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        return new TimeSpan((long)value);
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
    {
        return ((TimeSpan)value).TotalMilliseconds;
    }
}
